package test

import test.C.E1
import test.A.B.*

private enum class C {
    E1
}

class A {
    private class B {
        object C
        class D
    }

    fun test() {
        C
        D()
    }
}

fun box(): String {
    E1
    A().test()

    return "OK"
}