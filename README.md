To run the program:

1.) navigate to the project folder

2.) enter ./gradlew run --args=' [args] [pattern] [path] [commands] [command-args]'

    for example:
    ./gradlew run --args='--help'
    ./gradlew run --args='--no-heading somePattern /home.../.../ before-context -B 2'
    ./gradlew run --args='--ignore-case --no-heading anotherPattern /home.../... context-search -C 5'
