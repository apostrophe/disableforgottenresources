#!/bin/bash
set -eo pipefail
ARTIFACT_BUCKET=$(cat bucket-name.txt)

TEMPLATE=template-mvn.yml

aws cloudformation package --template-file $TEMPLATE --s3-bucket $ARTIFACT_BUCKET --output-template-file out.yml
#aws cloudformation deploy --template-file out.yml --stack-name disable-running-resources --capabilities CAPABILITY_NAMED_IAM 

# use "cron(0 8 * * ? *)" for normal CloudWatchRuleScheduleExpression 
aws cloudformation deploy --template-file out.yml --stack-name disable-running-resources --parameter-overrides CloudWatchRuleScheduleExpression="cron(0 8 * * ? *)" --capabilities CAPABILITY_NAMED_IAM 

# use "rate(5 minutes)" for debugging
# aws cloudformation deploy --template-file out.yml --stack-name disable-running-resources --parameter-overrides CloudWatchRuleScheduleExpression="rate(5 minutes)" --capabilities CAPABILITY_NAMED_IAM 

