This Program generates a printable PDF for RL-play from a LackeyCCG text file.

It expexts the follwoign format:
```
3 CardName1
5 CardName2
crypt
2 VampireCard1
4 VampireCard2
```

Build the program with

`gradlew build`

a Java 21 JDK is needed

Start the exectauble jar in the build folder with exatclty two arguments:

`java -jar lackey-to-pdf-1.0.0.jar <filename> <imagefolder>`
