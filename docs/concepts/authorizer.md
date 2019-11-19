# Authorizer

The authorizer is an SPNEGO-enabled web service.

All endpoints are authenticated by SPNEGO.

It serves three endpoints:

- `/`: Landing page for the Authorizer app.
- `/google/login`: Redirects user to Google OAuth 2.0 Login
- `/google/oauth2callback`: Accepts OAuth 2.0 authorization token, uses it to obtain a Refresh Token for the authenticated user.
  The Refresh Token is then encrypted and stored in the database.


# Settings

- `AUTHORIZER_HOST` bind address
- `AUTHORIZER_PORT` listen port
- `OAUTH_CLIENT_SECRET_JSON_PATH`


## Required Settings from other modules

- `DATABASE_BACKEND` Default: JDBCBackend
- `DATABASE_JDBC_URL` JDBC URL in format `jdbc:dialect:...`
- `ENCRYPTION_BACKEND` The authorizer encrypts all refresh tokens before storing them in the database.
