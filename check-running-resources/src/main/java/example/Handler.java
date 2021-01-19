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

		describeEc2Instances(context);


		String response = new String();
		// call Lambda API
		logger.info("Getting account settings");
		CompletableFuture<GetAccountSettingsResponse> accountSettings = 
				lambdaClient.getAccountSettings(GetAccountSettingsRequest.builder().build());
		// log execution details
		logger.info("ENVIRONMENT VARIABLES: {}", gson.toJson(System.getenv()));
		logger.info("CONTEXT: {}", gson.toJson(context));
		logger.info("EVENT: {}", gson.toJson(event));
		// process event
//		if (event != null) {
//			for(SQSMessage msg : event.getRecords()){
//				logger.info("~"+msg.getBody());
//			}
//		}
		// process Lambda API response
		try {
			GetAccountSettingsResponse settings = accountSettings.get();
			response = gson.toJson(settings.accountUsage());
			logger.info("Account usage: {}", response);
		} catch(Exception e) {
			e.getStackTrace();
		}
		return response;
	}
	
	private void describeEc2Instances(Context context) {
    	final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
    	boolean done = false;
    	
    	DescribeInstancesRequest request = new DescribeInstancesRequest();
		DescribeInstancesResult response = ec2.describeInstances(request);
		
		//if (response.getNextToken() == null)
		//	context.getLogger().log("No instances running!");
		
		out("--- RESOURCE START ---", context);
		
    	while (!done) {

    		for(Reservation reservation : response.getReservations()) {
    			for(Instance instance : reservation.getInstances()) {

					boolean instanceWithMatchingTagFound = false;

    				context.getLogger().log(String.format(
    						"Instance %s, " + 
    						"AMI: %s, " + 
    						"type: %s, " + 
    						"state: %s, ",
    						instance.getInstanceId(),
    						instance.getImageId(),
    						instance.getInstanceType(),
    						instance.getState()
    						));

				final String EC2_MARKER_TAG = System.getenv("STOP_EC2_INSTANCES_TAG_NAME");
				context.getLogger().log("*** STOP_EC2_INSTANCES_TAG_NAME " + EC2_MARKER_TAG + " ***");

				if (EC2_MARKER_TAG == null || EC2_MARKER_TAG.trim().isEmpty()) {
					context.getLogger().log("*** STOP_EC2_INSTANCES_TAG_NAME IS EMPTY ***");

					StopInstancesRequest stopRequest = new StopInstancesRequest().withInstanceIds(instance.getInstanceId());
					ec2.stopInstances(stopRequest);

					context.getLogger().log("*** request made to STOP instance " + instance.getInstanceId() + " ***");
				}
				else {
					context.getLogger().log("*** STOP_EC2_INSTANCES_TAG_NAME IS NOT EMPTY: "+EC2_MARKER_TAG+" ***");

					context.getLogger().log("instance.getState().getName()"+instance.getState().getName());

    				List<Tag> tags = instance.getTags();
    				for (Tag tag : tags) {
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
    		
    		request.setNextToken(response.getNextToken());
    		if (response.getNextToken() == null) done = true;
    	}
    	
		out("--- RESOURCE END ---", context);
		
	}
	
	private void out(String inString, Context context) {
		System.out.println(inString);
		context.getLogger().log(inString);
	}
}