export PS1="$ "
cd
mkdir -p target/term-screenshot
cd target/term-screenshot
echo '{:deps {lambdaisland/deep-diff2 {:mvn/version "2.0.108"}}}' > deps.edn
clear
clojure
(require '[lambdaisland.deep-diff2 :as ddiff])
(ddiff/pretty-print (ddiff/diff {:a 1 :b 2} {:a 1 :c 3}))
