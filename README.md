# to split a string in a Clojure

This project demonstrates various ways to split a string in a Clojure. When choosing a particular variant, do not forget to check the requirements on the inputs:

* some variants work with collection of characters and support 
  on the fly processing and infinite collections, some work only 
  on strings already loaded in the memory.
* do determine when to split a string, some variants take ordinary
  function, some just allows to specify char or set of chars on
  which to split
* some variants cannot return 'whitespace chunks', which may
  be required in some cases.

Similarly, when comparing which variant is better, consider following 
aspects:

* how much time it takes to produce a result. This is easily 
  measured with clojure.core/time or criterium benchmarking library
* how much memory will be allocated for the result. This can be 
  analyzed through any JVM profiler.
* how many garbage (temporary objects) is created while computing
  result. This is a creepy performance hit, which can be analyzed
  with commercial JVM profilers, e.g. JProfiler.

More notes:

* everything implemented as both reducible
  and foldable collection
  * except variants using lazy seqs
  * reducer variants support early termination e.g. with r/take
* most variants have limits on what is a delimiter, or
  how the result will look like
  * in return they deliver better performance
* two categories
  * partition-by on a collection of characters
  * splitting a string

## partition-by on collection of characters

* supports infinite collections
* does not need whole string in memory at once
* supports any predicate fn
* small memory usage

### lazy-seqs

* see [wagjo.split.algo.lazy](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/lazy.clj)
* very slow, should not be used, ever
* very high memory use, large amount of garbage
* easy technique, easy to reason about
* no parallel variant
* can keep whitespace chunks in the result
* `Reducer time: 3200 ms (4200 ms if keeping whitespace chunks)`
* `Folder time: N/A`

### naive iterative reducer/folder

* see [wagjo.split.algo.partitionby-naive](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/partitionby_naive.clj)
* slow but straightforward
* high memory use
* lot of garbage when folding, because of result wrapping
* parallel variant is actually slower, caused by wrapping 
  intermediate values in a map and producing huge amount of garbage.
* serves as a good base for understanding how things work 
  before we get to the optimized variant
* very flexible, can specify how to reduce/fold individual values 
  to create partitions
* `Reducer time: 1550 ms (2450 ms when keeping whitespace chunks)`
* `Folder time: 2500 ms (4000 ms when keeping whitespace chunks)`

### mutable iterative reducer/folder

* see [wagjo.split.algo.partitionby](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/partitionby.clj)
* faster flexible variant
  * uses mutation for intermediate results to lower the garbage
* little garbage
* very flexible, can specify how to reduce/fold individual values 
  to create partitions
* parallel variant really shines
* `Reducer time: 575 ms (866 ms when keeping whitespace chunks)`
* `Folder time: 213 ms (849 ms when keeping whitespace chunks)`
  * slow performance when keeping whitespace chunks may be because benchmarking hits current JVM memory limit?

### mutable iterative reducer/folder with shifting folder

* see [wagjo.split.algo.partitionby-shift](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/partitionby_shift.clj)
* fastest flexible variant
* avoids chunk/segment minigame when folding by providing custom folding function
* works only on collections which support random access
  * not much faster but the implementation is significantly simpler
* `Reducer time: same as previous`
* `Folder time: 189 ms (811 ms when keeping whitespace chunks)`

## split on string

* very fast because we have all data in memory
* larger memory usage
* often strips delimiters from the result
  * some variants return empty strings for subsequent delimiters
  * depends on use case whether this is good or not
* less flexible, each approach has its own limits on delimiters
  or at the returned result

### regex reducer/folder

* see [wagjo.split.algo.regex](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/regex.clj)
* any regex, uses matcher so that we support early termination of reduce
* fast, but keep in mind that you are using a cannon to shoot mosquitoes
* `Reducer time: 296 ms (445 ms when keeping whitespace chunks)`
* `Folder time: 84 ms (123 ms when keeping whitespace chunks)`

### StringTokenizer reducer/folder

* see [wagjo.split.algo.tokenizer](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/tokenizer.clj)
* takes set of delimiting chars
* fast, but StringTokenizer is deprecated in java
* `Reducer time: 134 ms (293 ms when keeping whitespace chunks)`
* `Folder time: 71 ms (120 ms when keeping whitespace chunks)`

### optimized iterative reducer/folder

* see [wagjo.split.algo.partitionby-string](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/partitionby_string.clj)
* like flexible partition-by, but optimized for strings
* `Reducer time: 225 ms (213 ms when keeping whitespace chunks)`
* `Folder time: 54 ms (71 ms when keeping whitespace chunks)`
* special variant which produces String-like objects which implements
  CharSequence and shares underlaying data with source string
* `Reducer time: 161 ms (140 ms when keeping whitespace chunks)`
* `Folder time: 42 ms (56 ms when keeping whitespace chunks)`
  
### indexOf reducer/folder

* see [wagjo.split.algo.lazy](https://github.com/wagjo/string-split/blob/master/src/clj/wagjo/split/algo/indexof.clj)
* THE fastest reducer/folder, but has specific limitations
  * delimiter is one character, or string (not implemented yet)
  * does not keep delimiters (whitespace chunks) in the result
* lowest memory use and almost no garbage (a tiny bit when folding)
* `Reducer time: 80 ms (136 ms when keeping whitespace chunks)`
* `Folder time: 37 ms (71 ms when keeping whitespace chunks)`
* special variant which produces String-like objects which implements
  CharSequence and shares underlaying data with source string
* `Reducer time: 52 ms (79 ms when keeping whitespace chunks)`
* `Folder time: 27 ms (54 ms when keeping whitespace chunks)`

## Machine

amd64 Linux 2.6.32-48-server 16 cpu(s)
Java HotSpot(TM) 64-Bit Server VM 23.25-b01
Runtime arguments: -XX:-UseConcMarkSweepGC -Dfile.encoding=UTF-8 -Dclojure.debug=false

## License

Copyright © 2013 Jozef Wagner.

Using parts of code from https://github.com/pmbauer/blogcode.text , Copyright (C) 2013, Paul Michael Bauer. All rights reserved.

The use and distribution terms for this software are covered by the [Eclipse Public License 1.0](http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.

By using this software in any fashion, you are agreeing to be bound by the terms of this license.

You must not remove this notice, or any other, from this software.
