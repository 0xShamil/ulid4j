#ulid4j

[![license](http://img.shields.io/badge/license-MIT-red.svg?style=flat)](https://github.com/0xShamil/ulid4j/blob/main/LICENSE) [![Build Status](https://travis-ci.org/0xShamil/ulid4j.svg?branch=main)](https://travis-ci.org/0xShamil/ulid4j.svg?branch=main)

`ulid4j` is a java library for generating stupidly fast [ULID](https://github.com/ulid/spec) (in less than 100ns).

## Design

A `ULID` is composed of two parts:

```
|----------|    |----------------|
 Timestamp          Randomness
   48bits             80bits
```
- The first 48 bits represents the amount of milliseconds since Unix Epoch, 1 January 1970. 

- The next 80 bits represent randomness and are generated using a secure random generator. However, instead of using java's `Random` or `SecureRandom` to generate the random bits, `ulid4j` uses a fast-key-erasure CSPRNG, implemented through a `SipHash-2-4`, to generate two random numbers. Since `SipHash` is a cryptographically strong PRF, the generated random numbers would be uniformly distributed.

When generating ULID, if the same millisecond is detected, the LSB of random part is simply incremented by 1 bit. However, once the millisecond changes, the random part is reset to a new value.

## Benchmark

This section shows benchmarks comparing `ulid4j` to [sulky-ulid](https://github.com/huxi/sulky/tree/master/sulky-ulid), [ulid-creator](https://github.com/f4b6a3/ulid-creator) and `java.util.UUID`.

System:  
- `JDK 1.8.0_272`
- `Intel(R) Core(TM) i7-6600U CPU @ 2.60GHz`
- `Ubuntu 20.04`

```
--------------------------------------------------------------------
THROUGHPUT            Mode    Cnt        Score       Error  Units
--------------------------------------------------------------------
ulid_creator          thrpt    5   4701028.527 ± 30450.135  ops/s
sulky_ulid            thrpt    5   1264087.411 ± 23769.113  ops/s
java_UUID             thrpt    5   1275500.236 ± 27089.688  ops/s
ulid4j                thrpt    5  13905799.018 ± 60290.838  ops/s
--------------------------------------------------------------------
```

```
--------------------------------------------------------------------
AVERAGE TIME          Mode   Cnt     Score    Error  Units
--------------------------------------------------------------------
ulid_creator          avgt    5    213.341 ±  1.964  ns/op
sulky_ulid            avgt    5    790.365 ±  2.956  ns/op
java_UUID             avgt    5    796.800 ±  5.221  ns/op
ulid4j                avgt    5     71.991 ±  0.423  ns/op
--------------------------------------------------------------------
```

## Usage

Before generating ULID string, a `ulid4j.Ulid()` instance needs to be created.

```java
    import ulid4j.Ulid;

    Ulid ulidGenerator = new Ulid();
```

This creates a `java.security.SecureRandom` underneath, however `Ulid(SecureRandom)` constructor can be used to provide a custom implementation as well. This `SecureRandom` is used to generate the initial `SipHash` states.

Once the `Ulid` instance gets created, the same should be used to generate multiple ULIDs (preferably until the JVM shutdowns).

- Get monotonically increasing ULID:
    ```java
        String ulid = ulidGenerator.next();
    ```
    Sequence of ULIDs that will be generated:
    ```
        timestamp   randomness
        |--------|---------------|
        01ENPZEF91HVP9WHSVE88GX8TD
        01ENPZEF91HVP9WHSVE88GX8TE
        01ENPZEF91HVP9WHSVE88GX8TF
        01ENPZEF91HVP9WHSVE88GX8TG
        01ENPZEF91HVP9WHSVE88GX8TH
        01ENPZEF91HVP9WHSVE88GX8TJ
        01ENPZEF91HVP9WHSVE88GX8TK
        01ENPZEF91HVP9WHSVE88GX8TM
        01ENPZEF91HVP9WHSVE88GX8TN
        01ENPZEF91HVP9WHSVE88GX8TP
        01ENPZEF91HVP9WHSVE88GX8TQ
        01ENPZEF91HVP9WHSVE88GX8TR
        01ENPZEF92FB0DCK7MVE8780NE  <-- millisecond changed
        01ENPZEF92FB0DCK7MVE8780NF
        01ENPZEF92FB0DCK7MVE8780NG
        01ENPZEF92FB0DCK7MVE8780NH
        01ENPZEF92FB0DCK7MVE8780NJ
        01ENPZEF92FB0DCK7MVE8780NK
        01ENPZEF92FB0DCK7MVE8780NM
        01ENPZEF92FB0DCK7MVE8780NN
        01ENPZEF92FB0DCK7MVE8780NP
        01ENPZEF92FB0DCK7MVE8780NQ
    ```
- Get a random ULID (monotonicity is not a requirement):
    ```java
        String ulid = ulidGenerator.create(); // 01ENPZEF91HVP9WHSVE88GX8TE
    ```
`Ulid` also contains static methods to 
- check if a `String` is a valid `Ulid`
```java
    String str1 = "01ENPZEF91HVP9WHSVE88GX8TE";
    String str2 = "0e144db1e46f442da30bcf6e31a20ca8";
    
    Ulid.isValid(str1); // true
    Ulid.isValid(str2); // false
```
- extract the unix time from a `Ulid` string
```java
    String ulidStr = ulidGenerator.next(); // 01ENQGX8XAF941RDPKPXSQQH4Z
    long unixTime = Ulid.unixTime(ulidStr);  // 1603886031786
```
  
## License
The source code is licensed under the [MIT License](https://github.com/0xShamil/ulid4j/blob/master/LICENSE).