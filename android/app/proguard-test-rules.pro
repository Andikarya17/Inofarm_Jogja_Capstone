# Error Prone's compile-time annotation in test dependencies references a javac-only enum.
# It is not available or used on Android; production optimization needs no extra keep rules.
-dontwarn javax.lang.model.element.Modifier
