import asyncio
from typing import Optional
from datetime import datetime
from ..config import settings

class EmailService:
    """Email service for sending notifications to users"""
    
    def __init__(self):
        self.use_mock = settings.USE_MOCK_EMAIL
        
    async def send_welcome_email(
        self,
        recipient_email: str,
        recipient_name: str,
        temp_password: str,
        login_url: str = "http://localhost:5173/login"
    ) -> bool:
        """Send welcome email with temporary credentials"""
        subject = "Welcome to BMSIT Faculty Portal"
        
        body = f"""
Dear {recipient_name},

Welcome to the BMSIT Faculty Portal!

Your account has been created by the administrator. Please use the following credentials to log in for the first time:

Email: {recipient_email}
Temporary Password: {temp_password}

IMPORTANT: This temporary password will expire in {settings.TEMP_PASSWORD_EXPIRY_DAYS} days.

Please follow these steps:
1. Visit {login_url}
2. Log in with your email and temporary password
3. You will be prompted to set a new password and complete your profile

For security reasons, please:
- Change your password immediately after logging in
- Do not share your credentials with anyone
- Use a strong password with a mix of uppercase, lowercase, numbers, and special characters

If you have any questions or face any issues, please contact the IT support team.

Best regards,
BMSIT Administration
"""
        
        return await self._send_email(recipient_email, subject, body)
    
    async def send_password_reset_email(
        self,
        recipient_email: str,
        recipient_name: str,
        reset_token: str,
        reset_url: str = "http://localhost:5173/reset-password"
    ) -> bool:
        """Send password reset email"""
        subject = "Password Reset Request - BMSIT Faculty Portal"
        
        body = f"""
Dear {recipient_name},

We received a request to reset your password for the BMSIT Faculty Portal.

Please click the link below to reset your password:
{reset_url}?token={reset_token}

This link will expire in 1 hour.

If you did not request a password reset, please ignore this email and contact IT support immediately.

Best regards,
BMSIT Administration
"""
        
        return await self._send_email(recipient_email, subject, body)
    
    async def send_credentials_resend_email(
        self,
        recipient_email: str,
        recipient_name: str,
        temp_password: str,
        login_url: str = "http://localhost:5173/login"
    ) -> bool:
        """Resend credentials to faculty who haven't completed setup"""
        subject = "BMSIT Faculty Portal - Credentials Reminder"
        
        body = f"""
Dear {recipient_name},

This is a reminder to complete your account setup on the BMSIT Faculty Portal.

Your login credentials:
Email: {recipient_email}
Temporary Password: {temp_password}

Please log in at: {login_url}

This temporary password will expire in {settings.TEMP_PASSWORD_EXPIRY_DAYS} days.

If you have already completed your setup, please ignore this email.

Best regards,
BMSIT Administration
"""
        
        return await self._send_email(recipient_email, subject, body)
    
    async def _send_email(self, to_email: str, subject: str, body: str) -> bool:
        """Internal method to send email (mock or real)"""
        if self.use_mock:
            # Mock email service - just log to console
            print("\n" + "="*80)
            print("📧 MOCK EMAIL SERVICE")
            print("="*80)
            print(f"To: {to_email}")
            print(f"Subject: {subject}")
            print("-"*80)
            print(body)
            print("="*80 + "\n")
            return True
        else:
            # Real email service using SMTP
            try:
                import aiosmtplib
                from email.mime.text import MIMEText
                from email.mime.multipart import MIMEMultipart
                
                message = MIMEMultipart()
                message["From"] = settings.SMTP_SENDER_EMAIL
                message["To"] = to_email
                message["Subject"] = subject
                message.attach(MIMEText(body, "plain"))
                
                await aiosmtplib.send(
                    message,
                    hostname=settings.SMTP_HOST,
                    port=settings.SMTP_PORT,
                    username=settings.SMTP_USERNAME,
                    password=settings.SMTP_PASSWORD,
                    use_tls=settings.SMTP_USE_TLS,
                )
                
                print(f"✅ Email sent successfully to {to_email}")
                return True
                
            except Exception as e:
                print(f"❌ Failed to send email to {to_email}: {str(e)}")
                return False

# Singleton instance
email_service = EmailService()
