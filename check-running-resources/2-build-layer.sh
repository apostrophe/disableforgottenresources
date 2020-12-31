#!/bin/bash
set -eo pipefail
#gradle -q packageLibs
#mv build/distributions/check-running-resources.zip build/check-running-resources-lib.zip

mvn package -Dmaven.test.skip=true 
