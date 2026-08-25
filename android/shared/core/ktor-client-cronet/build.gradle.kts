plugins {
    id("shared-library-plugin")
}

android {
    namespace = "com.inwords.expenses.core.ktor_client_cronet"
}

dependencies {
    implementation(shared.ktor.client.core)

    implementation(shared.cronet.api)
}
