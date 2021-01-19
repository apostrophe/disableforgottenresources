#!/bin/bash
set -eo pipefail
#gradle -q packageLibs
#mv build/distributions/disable-running-resources.zip build/disable-running-resources-lib.zip

mvn package -Dmaven.test.skip=true 
