# Various ways to split a string in Clojure

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

* very slow, should not be used ever

TODO

### naive iterative reducer/folder

* slow but straightforward

TODO

### mutable iterative reducer/folder

* fastest flexible variant

TODO

## split on string

* very fast because we have all data in memory
* larger memory usage
* often strips delimiters from the result
  * some variants return empty strings for subsequent delimiters
  * depends on use case whether this is good or not
* less flexible, each approach has its own limits on delimiters
  or at the returned result

### regex reducer/folder

* any regex, may return empty strings

TODO

### StringTokenizer reducer/folder

* set of delimiting chars

TODO

### optimized iterative reducer/folder

* like flexible partition-by, but optimized for strings

TODO
  
### indexOf reducer/folder

* THE fastest reducer/folder, but has specific limitations
  * delimiter is one character, or string (not implemented yet)
  * does not keep delimiters (whitespace chunks) in the result

```clojure  
;; benchmark reducer
(time (count (into [] (siof/split \space text))))
(with-progress-reporting
  (bench (into [] (siof/split \space text)) :verbose))
;; benchmark folder
(time (count (into [] (foldit (siof/split \space text)))))
(with-progress-reporting
  (bench (into [] (foldit (siof/split \space text))) :verbose))
;; Reducer time: 72.141531 ms
;; Folder time: 33.634083 ms
```

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
