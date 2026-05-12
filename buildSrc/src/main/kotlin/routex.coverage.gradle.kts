plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    currentProject {
        instrumentation {
            // The live online suite hits the real YAXI API; keep it out of the merged
            // report. Run `./gradlew :<m>:jvmOnlineTest koverHtmlReport` ad-hoc to
            // include it in a one-off invocation.
            disabledForTestTasks.add("jvmOnlineTest")
        }
    }
    reports {
        total {
            html { onCheck = false }
            xml { onCheck = false }
        }
    }
}
