To run the program:

1.) navigate to the project folder

2.) type: java -jar app/target/app.jar [options] [pattern] [path] [command] [arg]

    for example:
    java -jar app/target/app.jar --help
    java -jar app/target/app.jar -C 5 pattern /home/../../
    java -jar app/target/app.jar --color --no-heading -B 2 -A 4 pattern /home/../../
    java -jar app/target/app.jar -c -i somePattern /home/../../ context-search 5
    java -jar app/target/app.jar -c --no-heading anotherPattern /home/../../ after-context 3

