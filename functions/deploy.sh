#!/bin/bash

# Deployment script for Firebase Functions

echo "Installing dependencies..."
npm install

echo "Deploying Firebase Functions..."
firebase deploy --only functions

echo "Deployment complete!"
echo "Check the Firebase Console for any errors."