(ns art.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [art.core-test]
   [art.common-test]))

(enable-console-print!)

(doo-tests 'art.core-test
           'art.common-test)
