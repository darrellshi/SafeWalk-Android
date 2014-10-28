package edu.purdue.cs.cs180.safewalk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Observable;
import java.util.Observer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Purdue Computer Science SafeWalk Application for CS 18000 (Spring 2014)
 * 
 * Search this file for "STEP" items to complete the project.
 */
public class SafeWalk extends Activity implements Observer {
	// STEP 1: Update KEY and NICKNAME with your assigned key and choice of
	// nickname.
	private static final String KEY = "k839651";
	private static final String NICKNAME = "darrellshi";
	// END 1
	private static final String HOST = "pc.cs.purdue.edu";
	private static final int PORT = 1337;

	private Connector connector;
	private ArrayList<String> logs;
	private ArrayList<String> locations;
	private ArrayList<String> requests;
	private String currentLocation;
	private String score;

	private TextView locationTextView;
	private Spinner locationsSpinner;
	private TextView scoreTextView;
	private Spinner requestsSpinner;
	private Button moveSubmit;
	private Button requestSubmit;
	private TextView logsTextView;

	private Handler messageHandler;
	private SafeWalk safeWalk = this;

	/**
	 * This method is called when an Android application begins or is reset.
	 * (Think of it like "main".)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main); // The GUI is defined in
		// res/layout/main.xml

		// Establish connection to the SafeWalk server and initialize data
		// structures (the model)...
		connector = new Connector(HOST, PORT, String.format("connect %s", KEY),
				this);
		requests = new ArrayList<String>();
		locations = new ArrayList<String>();
		logs = new ArrayList<String>();

		// Get references to the "widgets" that make up the GUI...
		locationTextView = (TextView) findViewById(R.id.locationText);
		locationsSpinner = (Spinner) findViewById(R.id.moveSpinner);
		scoreTextView = (TextView) findViewById(R.id.scoreText);
		requestsSpinner = (Spinner) findViewById(R.id.requestSpinner);
		moveSubmit = (Button) findViewById(R.id.submitMove);
		requestSubmit = (Button) findViewById(R.id.submitWalk);
		logsTextView = (TextView) findViewById(R.id.distancesTextMultiline);
		logsTextView.setMovementMethod(new ScrollingMovementMethod());

		// Callback method: Invoked when the user presses the "Move" button...
		moveSubmit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// STEP 4: Get the selected string from the locationsSpinner and
				// send a "move" command to the server...
				// String location = name + " (" + x + ", " + y + ")";
				String location = (String) locationsSpinner.getSelectedItem();
				String subLocation = location.substring(0,
						location.indexOf('(') - 1);
				connector.writeLine("move " + subLocation);

				currentLocation = "Moving to " + subLocation + "...";
				locationTextView.setText(currentLocation);

			}
		});

		// Callback method: Invoked when the user presses the "Walk" button...
		requestSubmit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// STEP 5: Get the selected request from the requestsSpinner and
				// send a "walk" command to the server...

				String request = (String) requestsSpinner.getSelectedItem();
				String subFromLocation = request.substring(
						request.indexOf("from") + 5, request.indexOf("to") - 1);

				if (subFromLocation.equals(currentLocation)) {
					String subName = request.substring(
							request.indexOf("am ") + 3, request.indexOf('.'));

					String subToLocation = request.substring(
							request.indexOf("to") + 3,
							request.indexOf("the") - 6);
					//String subValue = request.substring(request
						//	.indexOf("value") + 6);
					connector.writeLine("walk " + subName);
					
					currentLocation = "Walking " + subName + " from " + subFromLocation + " to " + subToLocation + "...";
					locationTextView.setText(currentLocation);
					//score = Integer.toString(Integer.parseInt(score)
						//	+ Integer.parseInt(subValue));

					//scoreTextView.setText(score);
				}
				else {
					currentLocation = "You are not at " + subFromLocation + " yet";
					locationTextView.setText(currentLocation);
				}
			}
		});

		// Create a MessageHandler object to allow communication between the
		// Connector thread and the Android UI
		// thread...
		messageHandler = new MessageHandler();
	}

	/**
	 * Close the connection to the server. This method is called automatically
	 * when the application resets.
	 */
	@Override
	public void onStop() {
		connector.close();
		super.onStop();
	};

	/**
	 * Receive messages from the server on the Connector thread and pass them
	 * along to the Android UT thread for processing. This method is part of the
	 * Observer pattern. Messages sent from the Simulator to the Android are
	 * passed to this method by the Connector class.
	 */
	public void update(Observable arg1, Object arg2) {
		String message = (String) arg2;
		Log.w("SafeWalkMessage", message);

		// Since this method is invoked from the Connector class thread, we need
		// to pass the message
		// through a message handler to the Android UI thread.
		android.os.Message m = new android.os.Message();
		m.obj = message;
		messageHandler.sendMessage(m);
	}

	/**
	 * This nested class is needed to allow incoming messages from the
	 * Simulator, which arrive on one thread, to be dispatched for handling on
	 * the main Android UI thread.
	 */
	@SuppressLint("HandlerLeak")
	class MessageHandler extends Handler {

		/**
		 * Process incoming message. Split the message into words, then dispatch
		 * into appropriate method for handling.
		 */
		@Override
		public void handleMessage(android.os.Message m) {
			// Add the incoming message to the logs...
			String message = (String) m.obj;
			logs.add(message + "\n");

			// Convert the logs array to a single String, strip out all the
			// special characters, and put in "Log"
			// view for user to see...
			String processedLogs = logs.toString().replaceAll(",|\\]", "")
					.replaceAll("\\[", " ");
			logsTextView.setText(processedLogs);

			// Split the incoming message into fields and dispatch on the first
			// field...
			String[] fields = message.split(" ");
			if (fields[0].equals("location"))
				handleLocationMessage(fields);
			else if (fields[0].equals("request"))
				handleRequestMessage(fields);
			else if (fields[0].equals("volunteer"))
				handleVolunteerMessage(fields);
			else if (fields[0].equals("walking"))
				handleWalkingMessage(fields);
		}

		/**
		 * Handle a "location" message from the server. Add it to the list of
		 * locations to display.
		 */
		private void handleLocationMessage(String[] fields) {
			String name = fields[1];
			Double x = Double.parseDouble(fields[2]);
			Double y = Double.parseDouble(fields[3]);

			// STEP 2: Create a String representation of the "location" message,
			// add it to the requests ArrayList...
			String location = name + " (" + x + ", " + y + ")";
			locations.add(location);
			// sort the locations
			Collections.sort(locations);
			
			// Update the locations spinner with the current list of
			// locations...
			ArrayAdapter<String> locationsArrayAdapter = new ArrayAdapter<String>(
					safeWalk, android.R.layout.simple_spinner_dropdown_item,
					locations);
			locationsSpinner.setAdapter(locationsArrayAdapter);
		}

		/**
		 * Handle a "request" message from the server. Add it to the list of
		 * requests to display.
		 */
		private void handleRequestMessage(String[] fields) {
			String name = fields[1];
			String fromLocation = fields[2];
			String toLocation = fields[3];
			int value = Integer.parseInt(fields[4]);

			// STEP 3: Create a String representation of the "request" message,
			// add it to the locations ArrayList...
			String request = "I am " + name + ". Walk with me from "
					+ fromLocation + " to " + toLocation + " with the value "
					+ value;
			requests.add(request);
			
			// sort the requests in the order of scores get
			//String score = String.valueOf(value);
			//Collections.sort(requests, new Comparator<String>());
			
			// Update the requests spinner with the current list of requests...
			ArrayAdapter<String> requestsArrayAdapter = new ArrayAdapter<String>(
					safeWalk, android.R.layout.simple_spinner_dropdown_item,
					requests);
			requestsSpinner.setAdapter(requestsArrayAdapter);
		}

		/**
		 * Handle a "volunteer" message from the server. Just check to see if it
		 * is "me".
		 */
		private void handleVolunteerMessage(String[] fields) {
			// STEP 6: Check to see if the "volunteer" message identifies "me"
			// (compare to NICKNAME). If so, set the
			// currentLocation and score variables and use the setText(...)
			// method to update the locationTextView and
			// scoreTextView widgets.
			if (fields[1].equals(NICKNAME)) {
				currentLocation = fields[2];
				score = fields[3];
				locationTextView.setText(currentLocation);
				scoreTextView.setText(score);
			}
		}

		/**
		 * Handle a "walking" message from the server. Remove the corresponding
		 * "request" message from the system, since it is now being satisfied.
		 */
		private void handleWalkingMessage(String[] fields) {
			String requester = fields[2];
			// STEP 7: Loop through the requests ArrayList, looking for one that
			// matches the requester of this message.
			// If found, delete from the ArrayList.
			int index = -1;
			for (int i = 0; i < requests.size(); i++) {
				String request = requests.get(i);
				String subName = request.substring(
						request.indexOf("am ") + 3, request.indexOf('.'));
				if (subName.equals(requester))
					index = i;
			}
			if (index != -1)
				requests.remove(index);
			// Update the requests spinner with the current list of requests...
			ArrayAdapter<String> requestsArrayAdapter = new ArrayAdapter<String>(
					safeWalk, android.R.layout.simple_spinner_dropdown_item,
					requests);
			requestsSpinner.setAdapter(requestsArrayAdapter);
		}
	}
}
