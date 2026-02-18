const functions = require('firebase-functions');
const admin = require('firebase-admin');
const dialogflow = require('@google-cloud/dialogflow');
const chrono = require('chrono-node');
const path = require('path');

// Load .env from root directory (one level up from functions/)
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

try { admin.app(); } catch (e) { admin.initializeApp(); }
const db = admin.firestore();

// Import the refresh notifier function
const refreshNotifier = require('./refreshNotifier');
// Import the meeting start notifier function
const meetingStartNotifier = require('./meetingStartNotifier');
// Import the notification handler function
const notificationHandler = require('./notificationHandler');

// Export the refresh notification function
exports.sendRefreshNotification = refreshNotifier.sendRefreshNotification;
// Export the meeting start notification function
exports.sendMeetingStartNotification = meetingStartNotifier.sendMeetingStartNotification;
// Export the notification handler function
exports.handleNotification = notificationHandler.handleNotification;

exports.parseVoiceCommand = functions.https.onCall(async (data, context) => {
  const text = (data && data.text ? String(data.text) : '').toLowerCase();
  if (!text) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing text');
  }

  // Try Dialogflow first (if configured). If it fails or is not configured,
  // continue with local parsing below.
  try {
    const df = await tryDialogflow(text);
    if (df) return df;
  } catch (_) { /* ignore and fallback */ }

  // Attendees
  let attendees = 'All Faculty';
  if (/(\bhods\b|\bhod\b|head of department)/.test(text)) attendees = 'All HODs';
  else if (/(\bdeans\b|\bdean\b)/.test(text)) attendees = 'All Deans';

  // Location: capture after "at" or "in"
  let location = 'Not specified';
  const locMatch = text.match(/\b(?:at|in)\s+([a-z0-9#'\-\.\s]+)/);
  if (locMatch && locMatch[1]) location = locMatch[1].trim().replace(/\s+/g, ' ');

  // Date
  const now = new Date();
  const cal = new Date(now.getTime());
  const weekdays = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

  if (text.includes('day after tomorrow')) {
    cal.setDate(cal.getDate() + 2);
  } else if (text.includes('tomorrow')) {
    cal.setDate(cal.getDate() + 1);
  } else if (text.includes('today')) {
    // no change
  } else {
    // next weekday
    const wd = weekdays.findIndex(w => text.includes(w));
    if (wd >= 0) {
      const delta = (wd - cal.getDay() + 7) % 7 || 7;
      cal.setDate(cal.getDate() + delta);
    } else {
      // explicit date: 1/10[/2025] or 1-10[-2025]
      const m1 = text.match(/\b(\d{1,2})[\/\-](\d{1,2})(?:[\/\-](\d{2,4}))?\b/);
      if (m1) {
        const d = parseInt(m1[1], 10);
        const mo = parseInt(m1[2], 10) - 1;
        const y = m1[3] ? normalizeYear(parseInt(m1[3], 10)) : cal.getFullYear();
        cal.setFullYear(y, mo, d);
      } else {
        // month name formats: "1st october" or "october 1"
        const months = {
          january: 0, jan: 0, february: 1, feb: 1, march: 2, mar: 2, april: 3, apr: 3, may: 4,
          june: 5, jun: 5, july: 6, jul: 6, august: 7, aug: 7, september: 8, sep: 8, sept: 8,
          october: 9, oct: 9, november: 10, nov: 10, december: 11, dec: 11
        };
        for (const key in months) {
          if (text.includes(key)) {
            const dm = text.match(new RegExp(`\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+${key}\\b`));
            const md = text.match(new RegExp(`\\b${key}\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b`));
            const yMatch = text.match(/\b(20\d{2}|19\d{2})\b/);
            const day = dm ? parseInt(dm[1], 10) : (md ? parseInt(md[1], 10) : null);
            if (day) {
              const y = yMatch ? parseInt(yMatch[1], 10) : cal.getFullYear();
              cal.setFullYear(y, months[key], day);
              break;
            }
          }
        }
      }
    }
  }

  // Time: 16:30, 4:30 pm, 4pm, 09, 9 am
  const t = text.match(/\b(\d{1,2})(?::([0-5]\d))?\s*(am|pm)?\b/);
  if (t) {
    let h = parseInt(t[1], 10);
    const m = t[2] ? parseInt(t[2], 10) : 0;
    const ap = t[3] || '';
    if (ap === 'pm' && h >= 1 && h <= 11) h += 12;
    if (ap === 'am' && h === 12) h = 0;
    cal.setHours(Math.max(0, Math.min(23, h)), m, 0, 0);
  } else {
    cal.setHours(9, 0, 0, 0);
  }

  const title = attendees === 'All HODs' ? 'Meeting with HODs' : (attendees === 'All Deans' ? 'Meeting with Deans' : 'Faculty Meeting');
  return {
    title,
    attendees,
    location,
    dateTimeMillis: cal.getTime()
  };

  // Helper: attempt Dialogflow ES detectIntent if env var configured
  async function tryDialogflow(textLower) {
    const projectId = process.env.DIALOGFLOW_PROJECT_ID;
    if (!projectId) return null;
    const sessionClient = new dialogflow.SessionsClient();
    const sessionPath = sessionClient.projectAgentSessionPath(projectId, Math.random().toString(36).slice(2));
    const request = {
      session: sessionPath,
      queryInput: {
        text: { text: textLower, languageCode: 'en' }
      }
    };
    const [response] = await sessionClient.detectIntent(request);
    const qr = response.queryResult || {};
    const fields = (qr.parameters && qr.parameters.fields) || {};
    const attField = fields.attendees && (fields.attendees.stringValue || null);
    const locField = fields.location && (fields.location.stringValue || null);
    let whenMillis = null;
    const dtField = fields.datetime || fields.date_time || fields.date || null;
    const dtString = dtField && (dtField.stringValue || (dtField.structValue && dtField.structValue.fields && (dtField.structValue.fields.date_time || dtField.structValue.fields.datetime || dtField.structValue.fields.date) && (dtField.structValue.fields.date_time.stringValue || dtField.structValue.fields.datetime.stringValue || dtField.structValue.fields.date.stringValue)));
    if (dtString) {
      const d = chrono.parseDate(dtString, new Date(), { forwardDate: true });
      if (d) whenMillis = d.getTime();
    }
    const att = attField === 'All HODs' || /hod/i.test(attField || '') ? 'All HODs' : (attField === 'All Deans' || /dean/i.test(attField || '') ? 'All Deans' : (attField || 'All Faculty'));
    const loc = (locField && locField.trim()) || 'Not specified';
    const finalMillis = whenMillis || (chrono.parseDate(textLower, new Date(), { forwardDate: true }) || new Date(new Date().setHours(9, 0, 0, 0))).getTime();
    const titleDf = att === 'All HODs' ? 'Meeting with HODs' : (att === 'All Deans' ? 'Meeting with Deans' : 'Faculty Meeting');
    return { title: titleDf, attendees: att, location: loc, dateTimeMillis: finalMillis };
  }

  function normalizeYear(y) { return y < 100 ? 2000 + y : y; }
});

function formatTime(tsMillis) {
  return new Date(tsMillis).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

async function getTargetUids(meeting) {
  if (meeting.attendees === 'Custom' && Array.isArray(meeting.customAttendeeUids)) {
    // For custom attendees, we need to check each user's designation
    const validUids = [];
    for (const uid of meeting.customAttendeeUids) {
      try {
        const userDoc = await db.collection('users').doc(uid).get();
        if (userDoc.exists) {
          const userData = userDoc.data();
          validUids.push(uid);
        }
      } catch (error) {
        // If we can't get the user data, skip this user
        console.error('Error getting user data for uid:', uid, error);
      }
    }
    return validUids;
  }
  const roleMap = {
    'All Faculty': [
      'Faculty', 'Assistant Professor', 'Associate Professor', 'Lab Assistant', 'HOD', 'DEAN', 'ADMIN'
    ],
    'All HODs': ['HOD', 'ADMIN'],
    'All Deans': ['DEAN', 'ADMIN']
  };
  const allowed = roleMap[meeting.attendees] || [];
  if (!allowed.length) return [];
  const snap = await db.collection('users').where('designation', 'in', allowed).get();
  return snap.docs.map(d => d.id);
}

async function sendToTokens(tokens, payload) {
  if (!tokens.length) return;
  const message = { tokens, ...payload };
  const resp = await admin.messaging().sendEachForMulticast(message);
  // Cleanup invalid tokens
  const invalidTokens = [];
  resp.responses.forEach((r, i) => {
    if (!r.success) {
      const code = r.error && r.error.code;
      if (code === 'messaging/invalid-registration-token' || code === 'messaging/registration-token-not-registered') {
        invalidTokens.push(tokens[i]);
      }
    }
  });
  if (invalidTokens.length) {
    // Best-effort: remove invalid tokens from user docs
    const usersSnap = await db.collection('users').where('fcmToken', 'in', invalidTokens).get().catch(() => null);
    if (usersSnap) {
      const batch = db.batch();
      usersSnap.forEach(doc => batch.update(doc.ref, { fcmToken: admin.firestore.FieldValue.delete() }));
      await batch.commit().catch(() => null);
    }
  }
}

async function notifyUsersByUids(uids, notif) {
  if (!uids.length) return;
  const filteredUids = uids;

  const chunks = []; // chunk uids to limit getAll size
  const size = 10;
  for (let i = 0; i < filteredUids.length; i += size) chunks.push(filteredUids.slice(i, i + size));
  for (const chunk of chunks) {
    const refs = chunk.map(uid => db.collection('users').doc(uid));
    const docs = await db.getAll(...refs);
    const tokens = docs.map(d => (d.exists ? d.get('fcmToken') : null)).filter(Boolean);
    await sendToTokens(tokens, notif);
  }
}

exports.onMeetingCreate = functions.firestore.document('meetings/{meetingId}').onCreate(async (snap, ctx) => {
  const m = snap.data();
  if (!m || m.status !== 'Active') return;
  const uids = await getTargetUids(m);
  if (!uids.length) return;
  const dateMillis = m.dateTime && m.dateTime.toMillis ? m.dateTime.toMillis() : (m.dateTime && m.dateTime._seconds ? m.dateTime._seconds * 1000 : Date.now());
  const body = `Location: ${m.location || 'TBD'}`;
  const payload = {
    notification: {
      title: `New meeting: ${m.title || 'Meeting'}`,
      body
    },
    data: {
      type: 'created',
      title: m.title || 'Meeting',
      body,
      meetingId: ctx.params.meetingId
    }
  };
  await notifyUsersByUids(uids, payload);
});

exports.onMeetingUpdate = functions.firestore.document('meetings/{meetingId}').onUpdate(async (change, ctx) => {
  const before = change.before.data();
  const after = change.after.data();
  if (!after) return;
  const beforeMillis = before.dateTime && before.dateTime.toMillis ? before.dateTime.toMillis() : (before.dateTime && before.dateTime._seconds ? before.dateTime._seconds * 1000 : null);
  const afterMillis = after.dateTime && after.dateTime.toMillis ? after.dateTime.toMillis() : (after.dateTime && after.dateTime._seconds ? after.dateTime._seconds * 1000 : null);

  const uids = await getTargetUids(after);
  if (!uids.length) return;

  // Cancelled
  if (before.status === 'Active' && after.status === 'Cancelled') {
    const rel = beforeMillis ? formatRelativeDay(beforeMillis) : 'soon';
    const payload = {
      notification: {
        title: 'Meeting cancelled',
        body: `The meeting '${after.title || 'Meeting'}' which was expected ${rel} is cancelled.`
      },
      data: { type: 'cancelled', meetingId: ctx.params.meetingId }
    };
    await notifyUsersByUids(uids, payload);
    return;
  }

  // Rescheduled (still Active, date changed)
  if (after.status === 'Active' && beforeMillis && afterMillis && beforeMillis !== afterMillis) {
    const relOld = formatRelativeDay(beforeMillis);
    const relNew = formatRelativeDay(afterMillis);
    const movement = afterMillis > beforeMillis ? 'postponed' : 'preponed';
    const tNew = formatTime(afterMillis);
    const payload = {
      notification: {
        title: 'Meeting rescheduled',
        body: `The meeting '${after.title || 'Meeting'}' which was expected ${relOld} is ${movement} to ${relNew} at ${tNew}.`
      },
      data: { type: 'rescheduled', meetingId: ctx.params.meetingId }
    };
    await notifyUsersByUids(uids, payload);
  }
});

// Function to initialize meetings collection
exports.initializeMeetings = functions.https.onRequest(async (req, res) => {
  try {
    // Create and immediately delete a temporary document to initialize the collection
    const tempRef = db.collection('meetings').doc('temp-init');
    await tempRef.set({ initialized: true, timestamp: admin.firestore.FieldValue.serverTimestamp() });
    await tempRef.delete();

    res.status(200).send('Meetings collection initialized successfully');
  } catch (error) {
    console.error('Error initializing meetings collection:', error);
    res.status(500).send('Error initializing meetings collection: ' + error.message);
  }
});

// Function to automatically end meetings based on time constraints
exports.autoEndMeetings = functions.pubsub.schedule('every 5 minutes from 00:00 to 23:59').timeZone('Asia/Kolkata').onRun(async (context) => {
  try {
    const now = new Date();
    console.log(`Running autoEndMeetings at ${now.toString()}`);

    // Get all active meetings
    const snapshot = await db.collection('meetings')
      .where('status', '==', 'Active')
      .where('dateTime', '<=', admin.firestore.Timestamp.fromDate(now))
      .get();

    console.log(`Found ${snapshot.size} active meetings to check`);

    const batch = db.batch();
    let endedMeetings = 0;

    // Check if we're near the end-of-day cutoff for more aggressive processing
    const nearEndOfDayCutoff = isNearEndOfDayCutoff(now);

    for (const doc of snapshot.docs) {
      try {
        const meeting = doc.data();
        const meetingStartTime = meeting.dateTime.toDate();

        // Check if meeting should be ended automatically
        const shouldEnd = shouldAutoEndMeeting(meetingStartTime, now);

        // If we're near the end-of-day cutoff, be more aggressive about ending meetings
        // that started today
        let shouldEndAggressively = false;
        if (nearEndOfDayCutoff) {
          const today = new Date();
          today.setHours(0, 0, 0, 0);
          const meetingDate = new Date(meetingStartTime);
          meetingDate.setHours(0, 0, 0, 0);

          // If the meeting started today and we're near cutoff, end it
          if (meetingDate.getTime() === today.getTime()) {
            shouldEndAggressively = true;
          }
        }

        if (shouldEnd || shouldEndAggressively) {
          console.log(`Ending meeting ${doc.id} - shouldEnd: ${shouldEnd}, shouldEndAggressively: ${shouldEndAggressively}`);
          // Set end time to now
          batch.update(doc.ref, {
            endTime: admin.firestore.Timestamp.fromDate(now),
            status: 'Completed'
          });
          endedMeetings++;
        }
      } catch (docError) {
        console.error(`Error processing meeting ${doc.id}:`, docError);
      }
    }

    if (endedMeetings > 0) {
      await batch.commit();
      console.log(`Auto-ended ${endedMeetings} meetings`);
    } else {
      console.log('No meetings needed to be ended');
    }

    return null;
  } catch (error) {
    console.error('Error in autoEndMeetings:', error);
    return null;
  }
});

// Helper function to determine if a meeting should be auto-ended
function shouldAutoEndMeeting(startTime, currentTime) {
  const durationInMillis = currentTime.getTime() - startTime.getTime();

  // End if meeting has been going on for more than 6 hours
  if (durationInMillis > 6 * 60 * 60 * 1000) {
    return true;
  }

  // End if current time is past 9 PM
  const currentHour = currentTime.getHours();
  if (currentHour >= 21) { // 21:00 is 9 PM
    return true;
  }

  return false;
}

// Add a special function to check if we're close to the 9 PM cutoff
function isNearEndOfDayCutoff(currentTime) {
  const currentHour = currentTime.getHours();
  const currentMinute = currentTime.getMinutes();

  // If we're between 8:45 PM and 9:00 PM, we should be more aggressive about ending meetings
  if (currentHour === 20 && currentMinute >= 45) { // 8:45 PM
    return true;
  }

  // If we're past 9 PM
  if (currentHour >= 21) { // 9 PM
    return true;
  }

  return false;
}

// Export the refresh notification function
exports.sendRefreshNotification = refreshNotifier.sendRefreshNotification;

function formatRelativeDay(tsMillis) {
  const target = new Date(tsMillis);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const tday = new Date(target.getFullYear(), target.getMonth(), target.getDate());
  if (tday.getTime() === today.getTime()) return 'today';
  if (tday.getTime() === tomorrow.getTime()) return 'tomorrow';
  const fmt = target.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  return `on ${fmt}`;
}

// --- Voice NLU parsing endpoint (callable) ---
// Input: { text: string }
// Output: { title, attendees, location, dateTimeMillis }
exports.parseVoiceCommand = functions.https.onCall(async (data, context) => {
  const text = (data && data.text ? String(data.text) : '').toLowerCase();
  if (!text) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing text');
  }

  // Try Dialogflow first (if configured). If it fails or is not configured,
  // continue with local parsing below.
  try {
    const df = await tryDialogflow(text);
    if (df) return df;
  } catch (_) { /* ignore and fallback */ }

  // Attendees
  let attendees = 'All Faculty';
  if (/(\bhods\b|\bhod\b|head of department)/.test(text)) attendees = 'All HODs';
  else if (/(\bdeans\b|\bdean\b)/.test(text)) attendees = 'All Deans';

  // Location: capture after "at" or "in"
  let location = 'Not specified';
  const locMatch = text.match(/\b(?:at|in)\s+([a-z0-9#'\-\.\s]+)/);
  if (locMatch && locMatch[1]) location = locMatch[1].trim().replace(/\s+/g, ' ');

  // Date
  const now = new Date();
  const cal = new Date(now.getTime());
  const weekdays = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];

  if (text.includes('day after tomorrow')) {
    cal.setDate(cal.getDate() + 2);
  } else if (text.includes('tomorrow')) {
    cal.setDate(cal.getDate() + 1);
  } else if (text.includes('today')) {
    // no change
  } else {
    // next weekday
    const wd = weekdays.findIndex(w => text.includes(w));
    if (wd >= 0) {
      const delta = (wd - cal.getDay() + 7) % 7 || 7;
      cal.setDate(cal.getDate() + delta);
    } else {
      // explicit date: 1/10[/2025] or 1-10[-2025]
      const m1 = text.match(/\b(\d{1,2})[\/\-](\d{1,2})(?:[\/\-](\d{2,4}))?\b/);
      if (m1) {
        const d = parseInt(m1[1], 10);
        const mo = parseInt(m1[2], 10) - 1;
        const y = m1[3] ? normalizeYear(parseInt(m1[3], 10)) : cal.getFullYear();
        cal.setFullYear(y, mo, d);
      } else {
        // month name formats: "1st october" or "october 1"
        const months = {
          january: 0, jan: 0, february: 1, feb: 1, march: 2, mar: 2, april: 3, apr: 3, may: 4,
          june: 5, jun: 5, july: 6, jul: 6, august: 7, aug: 7, september: 8, sep: 8, sept: 8,
          october: 9, oct: 9, november: 10, nov: 10, december: 11, dec: 11
        };
        for (const key in months) {
          if (text.includes(key)) {
            const dm = text.match(new RegExp(`\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+${key}\\b`));
            const md = text.match(new RegExp(`\\b${key}\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b`));
            const yMatch = text.match(/\b(20\d{2}|19\d{2})\b/);
            const day = dm ? parseInt(dm[1], 10) : (md ? parseInt(md[1], 10) : null);
            if (day) {
              const y = yMatch ? parseInt(yMatch[1], 10) : cal.getFullYear();
              cal.setFullYear(y, months[key], day);
              break;
            }
          }
        }
      }
    }
  }

  // Time: 16:30, 4:30 pm, 4pm, 09, 9 am
  const t = text.match(/\b(\d{1,2})(?::([0-5]\d))?\s*(am|pm)?\b/);
  if (t) {
    let h = parseInt(t[1], 10);
    const m = t[2] ? parseInt(t[2], 10) : 0;
    const ap = t[3] || '';
    if (ap === 'pm' && h >= 1 && h <= 11) h += 12;
    if (ap === 'am' && h === 12) h = 0;
    cal.setHours(Math.max(0, Math.min(23, h)), m, 0, 0);
  } else {
    cal.setHours(9, 0, 0, 0);
  }

  const title = attendees === 'All HODs' ? 'Meeting with HODs' : (attendees === 'All Deans' ? 'Meeting with Deans' : 'Faculty Meeting');
  return {
    title,
    attendees,
    location,
    dateTimeMillis: cal.getTime()
  };

  // Helper: attempt Dialogflow ES detectIntent if env var configured
  async function tryDialogflow(textLower) {
    const projectId = process.env.DIALOGFLOW_PROJECT_ID;
    if (!projectId) return null;
    const sessionClient = new dialogflow.SessionsClient();
    const sessionPath = sessionClient.projectAgentSessionPath(projectId, Math.random().toString(36).slice(2));
    const request = {
      session: sessionPath,
      queryInput: {
        text: { text: textLower, languageCode: 'en' }
      }
    };
    const [response] = await sessionClient.detectIntent(request);
    const qr = response.queryResult || {};
    const fields = (qr.parameters && qr.parameters.fields) || {};
    const attField = fields.attendees && (fields.attendees.stringValue || null);
    const locField = fields.location && (fields.location.stringValue || null);
    let whenMillis = null;
    const dtField = fields.datetime || fields.date_time || fields.date || null;
    const dtString = dtField && (dtField.stringValue || (dtField.structValue && dtField.structValue.fields && (dtField.structValue.fields.date_time || dtField.structValue.fields.datetime || dtField.structValue.fields.date) && (dtField.structValue.fields.date_time.stringValue || dtField.structValue.fields.datetime.stringValue || dtField.structValue.fields.date.stringValue)));
    if (dtString) {
      const d = chrono.parseDate(dtString, new Date(), { forwardDate: true });
      if (d) whenMillis = d.getTime();
    }
    const att = attField === 'All HODs' || /hod/i.test(attField || '') ? 'All HODs' : (attField === 'All Deans' || /dean/i.test(attField || '') ? 'All Deans' : (attField || 'All Faculty'));
    const loc = (locField && locField.trim()) || 'Not specified';
    const finalMillis = whenMillis || (chrono.parseDate(textLower, new Date(), { forwardDate: true }) || new Date(new Date().setHours(9, 0, 0, 0))).getTime();
    const titleDf = att === 'All HODs' ? 'Meeting with HODs' : (att === 'All Deans' ? 'Meeting with Deans' : 'Faculty Meeting');
    return { title: titleDf, attendees: att, location: loc, dateTimeMillis: finalMillis };
  }

  function normalizeYear(y) { return y < 100 ? 2000 + y : y; }
});

function formatTime(tsMillis) {
  return new Date(tsMillis).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

async function getTargetUids(meeting) {
  if (meeting.attendees === 'Custom' && Array.isArray(meeting.customAttendeeUids)) {
    // For custom attendees, we need to check each user's designation
    const validUids = [];
    for (const uid of meeting.customAttendeeUids) {
      try {
        const userDoc = await db.collection('users').doc(uid).get();
        if (userDoc.exists) {
          const userData = userDoc.data();
          validUids.push(uid);
        }
      } catch (error) {
        // If we can't get the user data, skip this user
        console.error('Error getting user data for uid:', uid, error);
      }
    }
    return validUids;
  }
  const roleMap = {
    'All Faculty': [
      'Faculty', 'Assistant Professor', 'Associate Professor', 'Lab Assistant', 'HOD', 'DEAN', 'ADMIN'
    ],
    'All HODs': ['HOD', 'ADMIN'],
    'All Deans': ['DEAN', 'ADMIN']
  };
  const allowed = roleMap[meeting.attendees] || [];
  if (!allowed.length) return [];
  const snap = await db.collection('users').where('designation', 'in', allowed).get();
  return snap.docs.map(d => d.id);
}

async function sendToTokens(tokens, payload) {
  if (!tokens.length) return;
  const message = { tokens, ...payload };
  const resp = await admin.messaging().sendEachForMulticast(message);
  // Cleanup invalid tokens
  const invalidTokens = [];
  resp.responses.forEach((r, i) => {
    if (!r.success) {
      const code = r.error && r.error.code;
      if (code === 'messaging/invalid-registration-token' || code === 'messaging/registration-token-not-registered') {
        invalidTokens.push(tokens[i]);
      }
    }
  });
  if (invalidTokens.length) {
    // Best-effort: remove invalid tokens from user docs
    const usersSnap = await db.collection('users').where('fcmToken', 'in', invalidTokens).get().catch(() => null);
    if (usersSnap) {
      const batch = db.batch();
      usersSnap.forEach(doc => batch.update(doc.ref, { fcmToken: admin.firestore.FieldValue.delete() }));
      await batch.commit().catch(() => null);
    }
  }
}

async function notifyUsersByUids(uids, notif) {
  if (!uids.length) return;
  const filteredUids = uids;

  const chunks = []; // chunk uids to limit getAll size
  const size = 10;
  for (let i = 0; i < filteredUids.length; i += size) chunks.push(filteredUids.slice(i, i + size));
  for (const chunk of chunks) {
    const refs = chunk.map(uid => db.collection('users').doc(uid));
    const docs = await db.getAll(...refs);
    const tokens = docs.map(d => (d.exists ? d.get('fcmToken') : null)).filter(Boolean);
    await sendToTokens(tokens, notif);
  }
}

exports.onMeetingCreate = functions.firestore.document('meetings/{meetingId}').onCreate(async (snap, ctx) => {
  const m = snap.data();
  if (!m || m.status !== 'Active') return;
  const uids = await getTargetUids(m);
  if (!uids.length) return;
  const dateMillis = m.dateTime && m.dateTime.toMillis ? m.dateTime.toMillis() : (m.dateTime && m.dateTime._seconds ? m.dateTime._seconds * 1000 : Date.now());
  const body = `Location: ${m.location || 'TBD'}`;
  const payload = {
    notification: {
      title: `New meeting: ${m.title || 'Meeting'}`,
      body
    },
    data: {
      type: 'created',
      title: m.title || 'Meeting',
      body,
      meetingId: ctx.params.meetingId
    }
  };
  await notifyUsersByUids(uids, payload);
});

exports.onMeetingUpdate = functions.firestore.document('meetings/{meetingId}').onUpdate(async (change, ctx) => {
  const before = change.before.data();
  const after = change.after.data();
  if (!after) return;
  const beforeMillis = before.dateTime && before.dateTime.toMillis ? before.dateTime.toMillis() : (before.dateTime && before.dateTime._seconds ? before.dateTime._seconds * 1000 : null);
  const afterMillis = after.dateTime && after.dateTime.toMillis ? after.dateTime.toMillis() : (after.dateTime && after.dateTime._seconds ? after.dateTime._seconds * 1000 : null);

  const uids = await getTargetUids(after);
  if (!uids.length) return;

  // Cancelled
  if (before.status === 'Active' && after.status === 'Cancelled') {
    const rel = beforeMillis ? formatRelativeDay(beforeMillis) : 'soon';
    const payload = {
      notification: {
        title: 'Meeting cancelled',
        body: `The meeting '${after.title || 'Meeting'}' which was expected ${rel} is cancelled.`
      },
      data: { type: 'cancelled', meetingId: ctx.params.meetingId }
    };
    await notifyUsersByUids(uids, payload);
    return;
  }

  // Rescheduled (still Active, date changed)
  if (after.status === 'Active' && beforeMillis && afterMillis && beforeMillis !== afterMillis) {
    const relOld = formatRelativeDay(beforeMillis);
    const relNew = formatRelativeDay(afterMillis);
    const movement = afterMillis > beforeMillis ? 'postponed' : 'preponed';
    const tNew = formatTime(afterMillis);
    const payload = {
      notification: {
        title: 'Meeting rescheduled',
        body: `The meeting '${after.title || 'Meeting'}' which was expected ${relOld} is ${movement} to ${relNew} at ${tNew}.`
      },
      data: { type: 'rescheduled', meetingId: ctx.params.meetingId }
    };
    await notifyUsersByUids(uids, payload);
  }
});

// Function to initialize meetings collection
exports.initializeMeetings = functions.https.onRequest(async (req, res) => {
  try {
    // Create and immediately delete a temporary document to initialize the collection
    const tempRef = db.collection('meetings').doc('temp-init');
    await tempRef.set({ initialized: true, timestamp: admin.firestore.FieldValue.serverTimestamp() });
    await tempRef.delete();

    res.status(200).send('Meetings collection initialized successfully');
  } catch (error) {
    console.error('Error initializing meetings collection:', error);
    res.status(500).send('Error initializing meetings collection: ' + error.message);
  }
});

// Function to automatically end meetings based on time constraints
exports.autoEndMeetings = functions.pubsub.schedule('every 5 minutes from 00:00 to 23:59').timeZone('Asia/Kolkata').onRun(async (context) => {
  try {
    const now = new Date();
    console.log(`Running autoEndMeetings at ${now.toString()}`);

    // Get all active meetings
    const snapshot = await db.collection('meetings')
      .where('status', '==', 'Active')
      .where('dateTime', '<=', admin.firestore.Timestamp.fromDate(now))
      .get();

    console.log(`Found ${snapshot.size} active meetings to check`);

    const batch = db.batch();
    let endedMeetings = 0;

    // Check if we're near the end-of-day cutoff for more aggressive processing
    const nearEndOfDayCutoff = isNearEndOfDayCutoff(now);

    for (const doc of snapshot.docs) {
      try {
        const meeting = doc.data();
        const meetingStartTime = meeting.dateTime.toDate();

        // Check if meeting should be ended automatically
        const shouldEnd = shouldAutoEndMeeting(meetingStartTime, now);

        // If we're near the end-of-day cutoff, be more aggressive about ending meetings
        // that started today
        let shouldEndAggressively = false;
        if (nearEndOfDayCutoff) {
          const today = new Date();
          today.setHours(0, 0, 0, 0);
          const meetingDate = new Date(meetingStartTime);
          meetingDate.setHours(0, 0, 0, 0);

          // If the meeting started today and we're near cutoff, end it
          if (meetingDate.getTime() === today.getTime()) {
            shouldEndAggressively = true;
          }
        }

        if (shouldEnd || shouldEndAggressively) {
          console.log(`Ending meeting ${doc.id} - shouldEnd: ${shouldEnd}, shouldEndAggressively: ${shouldEndAggressively}`);
          // Set end time to now
          batch.update(doc.ref, {
            endTime: admin.firestore.Timestamp.fromDate(now),
            status: 'Completed'
          });
          endedMeetings++;
        }
      } catch (docError) {
        console.error(`Error processing meeting ${doc.id}:`, docError);
      }
    }

    if (endedMeetings > 0) {
      await batch.commit();
      console.log(`Auto-ended ${endedMeetings} meetings`);
    } else {
      console.log('No meetings needed to be ended');
    }

    return null;
  } catch (error) {
    console.error('Error in autoEndMeetings:', error);
    return null;
  }
});

// Helper function to determine if a meeting should be auto-ended
function shouldAutoEndMeeting(startTime, currentTime) {
  const durationInMillis = currentTime.getTime() - startTime.getTime();

  // End if meeting has been going on for more than 6 hours
  if (durationInMillis > 6 * 60 * 60 * 1000) {
    return true;
  }

  // End if current time is past 9 PM
  const currentHour = currentTime.getHours();
  if (currentHour >= 21) { // 21:00 is 9 PM
    return true;
  }

  return false;
}

// Add a special function to check if we're close to the 9 PM cutoff
function isNearEndOfDayCutoff(currentTime) {
  const currentHour = currentTime.getHours();
  const currentMinute = currentTime.getMinutes();

  // If we're between 8:45 PM and 9:00 PM, we should be more aggressive about ending meetings
  if (currentHour === 20 && currentMinute >= 45) { // 8:45 PM
    return true;
  }

  // If we're past 9 PM
  if (currentHour >= 21) { // 9 PM
    return true;
  }

  return false;
}
