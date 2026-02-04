/**
 * Production-ready email: Gmail OAuth MailSender, automatic token refresh, multi-tenant facade.
 *
 * <ul>
 *   <li>{@link com.biasharahub.mail.EmailSender} – abstraction for sending (Gmail OAuth or fallback).</li>
 *   <li>{@link com.biasharahub.mail.GmailOAuthEmailSender} – Gmail API with OAuth2 refresh token; token refreshed on schedule.</li>
 *   <li>{@link com.biasharahub.mail.MultiTenantEmailSender} – resolves tenant from context and delegates to default sender.</li>
 * </ul>
 */
package com.biasharahub.mail;
