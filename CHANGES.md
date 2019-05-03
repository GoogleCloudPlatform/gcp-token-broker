## 0.2.0 (Pending)

- Rewrote server application from Python to Java.
- Simplified KDC trust topology. Now the Broker service does not
  require its own KDC and does not require any connectivity with the
  origin KDC or AD. Also, it is now required to upload a separate
  keytab for each realm so the broker service can authenticate users.

## 0.1.0 (April 5, 2019)

Initial release.