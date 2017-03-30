fun <T: Any> testing(a: T?) = a is <!CANNOT_CHECK_FOR_ERASED!>T<!>
