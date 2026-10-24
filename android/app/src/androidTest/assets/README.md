# Local TLS test fixture

mock-server.p12 is a deliberately public, test-only key pair for MockWebServer on localhost. Its fixture password is defined in HttpsFixture.kt. It is loaded from the instrumentation APK context, never the application context, and is not a server, device, owner or production credential. Do not reuse it outside isolated tests. The fixture client alone trusts this certificate; the default application client does not.
