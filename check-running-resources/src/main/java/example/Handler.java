package example;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import software.amazon.awssdk.services.lambda.model.GetAccountSettingsRequest;
import software.amazon.awssdk.services.lambda.model.GetAccountSettingsResponse;
import software.amazon.awssdk.services.lambda.model.ServiceException;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.AccountUsage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.StringBuilder;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Handler value: example.Handler
public class Handler implements RequestHandler<SQSEvent, String>{
	private static final Logger logger = LoggerFactory.getLogger(Handler.class);
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static final LambdaAsyncClient lambdaClient = LambdaAsyncClient.create();
	public Handler(){
		CompletableFuture<GetAccountSettingsResponse> accountSettings = lambdaClient.getAccountSettings(GetAccountSettingsRequest.builder().build());
		try {
			GetAccountSettingsResponse settings = accountSettings.get();
		} catch(Exception e) {
			e.getStackTrace();
		}
	}
	@Override
	public String handleRequest(SQSEvent event, Context context)
	{
		// logger.info("ENVIRONMENT VARIABLES: {}", gson.toJson(System.getenv()));
		// logger.info("CONTEXT: {}", gson.toJson(context));
		// logger.info("EVENT: {}", gson.toJson(event));

		disableEc2Instances(context);

		String response = new String();
		return response;
	}
	
	private void disableEc2Instances(Context context) {		
		String currentMethodName = new Object(){}.getClass().getEnclosingMethod().getName();
		context.getLogger().log("--- "+currentMethodName+"() START ---");
		
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
	             //.withRegion("us-west-2")
				.build();
		
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		DescribeInstancesResult response = ec2.describeInstances(request);
		
		List<Reservation> reservations = response.getReservations();

		if (reservations.isEmpty()) {
			context.getLogger().log("There are no instances running in this region");
			return;
		}

		// check all ec2 instances in the region..
		for(Reservation reservation : reservations) {

			List<Instance> instances = reservation.getInstances();

			boolean instanceWithMatchingTagFound = false;

			for(Instance instance : instances) {

				context.getLogger().log(String.format(
						"Instance %s, " + 
						"AMI: %s, " + 
						"type: %s, " + 
						"state: %s, " +
						"tags: %s, ",
						instance.getInstanceId(),
						instance.getImageId(),
						instance.getInstanceType(),
						instance.getState(),
						instance.getTags().toString()
						));
				
				// skip instances that aren't in the running state
				if (!instance.getState().getName().equalsIgnoreCase("RUNNING")) {
					context.getLogger().log(String.format("Instance %s is not running (state: %s)", instance.getInstanceId(), instance.getState()));
					continue;
				}

				// extract reserved tag from lambda 
				final String EC2_MARKER_TAG = System.getenv("STOP_EC2_INSTANCES_TAG_NAME");
				context.getLogger().log("lambda's tag STOP_EC2_INSTANCES_TAG_NAME: " + EC2_MARKER_TAG);

				// if there's no lambda tag value defined...
				if (EC2_MARKER_TAG == null || EC2_MARKER_TAG.trim().isEmpty()) {

					// ...stop all running instances
					StopInstancesRequest stopRequest = new StopInstancesRequest().withInstanceIds(instance.getInstanceId());
					ec2.stopInstances(stopRequest);

					context.getLogger().log("*** request made to STOP instance " + instance.getInstanceId() + " ***");
				}
				else {
					// otherwise there must be a tag value defined

					context.getLogger().log("*** STOP_EC2_INSTANCES_TAG_NAME IS NOT EMPTY: "+EC2_MARKER_TAG+" ***");
					context.getLogger().log("instance.getState().getName()"+instance.getState().getName());

					// get the tags from each instance
					List<Tag> tags = instance.getTags();
					for (Tag tag : tags) {
						// if any of the instance tags match the lambda tag, stop it
						if (tag.getKey().equalsIgnoreCase(EC2_MARKER_TAG) && instance.getState().getName().equalsIgnoreCase("running")) {
							context.getLogger().log("*** EC2 TAG FOUND:  "+tag.getKey()+" ***");
		
							StopInstancesRequest stopRequest = new StopInstancesRequest().withInstanceIds(instance.getInstanceId());
							ec2.stopInstances(stopRequest);
							
							context.getLogger().log("*** request made to STOP instance " + instance.getInstanceId() + " ***");
							instanceWithMatchingTagFound = true;

							// add SNS notification
						}
					}

					if (!instanceWithMatchingTagFound)
						context.getLogger().log("*** No tags found matching " + EC2_MARKER_TAG + " on instance id " + instance.getInstanceId() + " ***");
				}
			}

		}
		
		// request.setNextToken(instances.getNextToken());
		// if (instances.getNextToken() == null) 
		// 	done = true;
		
		context.getLogger().log("--- "+currentMethodName+"() END ---");
		
	}
	
	private void out(String inString, Context context) {
		context.getLogger().log(inString);
		context.getLogger().log(inString);
	}
}