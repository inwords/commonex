tasks.register("clean") {
    group = "build"
    description = "Cleans all subprojects"

    dependsOn(subprojects.map { it.tasks.named("clean") })
}
