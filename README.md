# HeapDumpPlugin

> A Godzilla plugin for heap dumping and analysis.

## How to use

If you only want to dump the heap, click `Heap Dump`.

If you want to analyze the heap dump file, you should click the `Load` button to prepare the loader on the remote server;
then select `With Spider`, when you click `Heap Dump`, it will dump and analyze it.

> The `Heap Dump` button would disable when you select `With Spider` without the `Load` button clicked.

It's easy to clean the heap dump file when the remote server is Linux, select `Delete After Spider`, `hprof` file will be deleted after analysis.

## How to build

> You can download it from the release.

1. build the code running on the remote server

    ```bash
    javac -d ../resources -source 1.5 -target 1.5 org/apache/common/hotspot/HotSpotLoader.java
    javac -d ../resources -source 1.5 -target 1.5 org/apache/common/VirtualHeap.java
    ```

2. exclude the `org` folder; copy the `resources` folder to jar root, then build the artifact.

## Reference

* https://github.com/whwlsfb/JDumpSpider