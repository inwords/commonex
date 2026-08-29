plugins {
    id("shared-library-plugin")
    alias(shared.plugins.android.junit5)
}

android {
    namespace = "com.inwords.expenses.core.ktor_client_cronet"
}

dependencies {
    implementation(shared.ktor.client.core)

    implementation(shared.cronet)

    testImplementation(shared.coroutines.test)
    testImplementation(shared.junit.jupiter.api)
    testRuntimeOnly(shared.junit.jupiter.engine)
}
