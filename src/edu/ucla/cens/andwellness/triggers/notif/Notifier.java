package edu.ucla.cens.andwellness.triggers.notif;


import java.util.List;
import java.util.Set;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import edu.ucla.cens.andwellness.R;

public class Notifier {

	private static final String DEBUG_TAG = "TriggerFramework";
	//TODO
	private static final int NOIF_ID = 100;
		
	private static final String ACTION_TRIGGER_NOTIFICATION = 
		"edu.ucla.cens.andwellness.triggers.TRIGGER_NOTIFICATION";
	private static final String ACTION_ACTIVE_SURVEY_LIST_CHANGED = 
		"edu.ucla.cens.andwellness.triggers.SURVEY_LIST_CHANGED";
		
	private static final String ACTION_NOTIF_CLICKED = 
			"edu.ucla.cens.triggers.notif.Notifier.notification_clicked";
	private static final String ACTION_NOTIF_DELETED = 
			"edu.ucla.cens.triggers.notif.Notifier.notification_deleted";
	private static final String ACTION_EXPIRE_ALM = 
			"edu.ucla.cens.triggers.notif.Notifier.expire_notif";
	private static final String ACTION_REPEAT_ALM = 
			"edu.ucla.cens.triggers.notif.Notifier.repeat_notif";
	private static final String DATA_PREFIX_ALM = 
			"notifier://edu.ucla.cens.triggers.notif.Notifier/";
	
	private static final String KEY_TRIGGER_ID = 
			Notifier.class.getName() + ".trigger_id";
	private static final String KEY_REPEAT_LIST = 
			Notifier.class.getName() + ".repeat_list";
	
	private static final String KEY_NOTIF_VISIBILITY_PREF = 
			"notif_visibility";
	

	private static void hideNotification(Context context) {
		NotificationManager notifMan = (NotificationManager)context.getSystemService(
										Context.NOTIFICATION_SERVICE);
		
		notifMan.cancel(NOIF_ID);
		saveNotifVisibility(context, false);
	}
	
	private static void displayNotification(Context context,
											String title,
											String summary,
											boolean quiet) {
		
		if(quiet && !getNotifVisibility(context)) {
			return;
		}
		
		NotificationManager notifMan = (NotificationManager)context.getSystemService(
										Context.NOTIFICATION_SERVICE);
		
		
		Notification notif = new Notification(R.drawable.apple_logo,
											  title, System.currentTimeMillis());

		if(!quiet) {
			notif.defaults = Notification.DEFAULT_ALL;
		}
		else {
			notif.tickerText = null;
		}
		
		notif.deleteIntent = PendingIntent.getBroadcast(context, 0, 
											new Intent(ACTION_NOTIF_DELETED),
											PendingIntent.FLAG_CANCEL_CURRENT);
			
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, 
											new Intent(ACTION_NOTIF_CLICKED),
											PendingIntent.FLAG_CANCEL_CURRENT);
		
		notif.setLatestEventInfo(context, title, summary, pi);
		notifMan.notify(NOIF_ID, notif);
		saveNotifVisibility(context, true);
	}
	
	private static String getSurveyDisplayList(Set<String> surveys) {
		String ret = "";
		
		int i = 0;
		for(String survey : surveys) {
			ret += survey;
			
			i++;
			if(i < surveys.size()) {
				ret += ", ";
			}
		}
		
		return ret;
	}
	
	public static void refreshNotification(Context context, boolean quiet) {
		
		Log.i(DEBUG_TAG, "Notifier: Refreshing notification, quiet = " + quiet);
		
		Set<String> actSurveys = NotifSurveyAdaptor.getAllActiveSurveys(context);
		
		if(actSurveys.size() == 0) {
			Log.i(DEBUG_TAG, "Notifier: No active surveys");
			hideNotification(context);
		}
		else {
			String title = "You have " + actSurveys.size() + 
							" survey" + (actSurveys.size() != 1 ? "s" : "") +
							" to take";
			
			displayNotification(context, title,
								getSurveyDisplayList(actSurveys), quiet);
		}
		
		context.sendBroadcast(new Intent(ACTION_ACTIVE_SURVEY_LIST_CHANGED));
	}
	
	private static Intent getAlarmIntent(String action, int trigId) {
		Intent i = new Intent(action);
		i.setData(Uri.parse(DATA_PREFIX_ALM + trigId));
		i.putExtra(KEY_TRIGGER_ID, trigId);
		return i;
	}
	
	private static void cancelAllAlarms(Context context, int trigId) {
		AlarmManager alarmMan = (AlarmManager) context.getSystemService(
									Context.ALARM_SERVICE);

		Intent i = getAlarmIntent(ACTION_EXPIRE_ALM, trigId);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 
						   PendingIntent.FLAG_NO_CREATE);
		
		if(pi != null) {
			alarmMan.cancel(pi);
			pi.cancel();
		}
		
		i = getAlarmIntent(ACTION_REPEAT_ALM, trigId);
		pi = PendingIntent.getBroadcast(context, 0, i, 
			 PendingIntent.FLAG_NO_CREATE);
		
		if(pi != null) {
			alarmMan.cancel(pi);
			pi.cancel();
		}
	}
	
	private static void setAlarm(Context context, 
						  		 String action, 
						  		 int trigId, 
						  		 int mins,
						  		 Bundle extras) {
		
		Log.i(DEBUG_TAG, "Notifier: Setting alarm(" + trigId + 
						 ", " + mins + ", " + action + ")");
		
		AlarmManager alarmMan = (AlarmManager) 
								context.getSystemService(Context.ALARM_SERVICE);
		
		Intent i = getAlarmIntent(action, trigId);
		if(extras != null) {
			i.putExtras(extras);
		}
		
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 
						   PendingIntent.FLAG_CANCEL_CURRENT);

		long elapsed = mins * 60 * 1000;
		
		alarmMan.set(AlarmManager.RTC_WAKEUP,
					 System.currentTimeMillis() + elapsed, pi);
	}
	
	private static void setRepeatAlarm(Context context, int trigId, 
									   int[] repeatDiffs) {
		
		if(repeatDiffs.length == 0) {
			//No more repeats in the list
			return;
		}
		
		//Set a repeat reminder for the first item in the list
		//and prepare the new list by removing this item
		
		int[] newRepeats = new int[repeatDiffs.length - 1];
		System.arraycopy(repeatDiffs, 1, newRepeats, 0, repeatDiffs.length -1);
		
		Bundle repeatBundle = new Bundle();
		repeatBundle.putIntArray(KEY_REPEAT_LIST, newRepeats);
		//Set the alarm for the first repeat item and attach the remaining list
		setAlarm(context, ACTION_REPEAT_ALM, trigId, 
				repeatDiffs[0], repeatBundle);
	}
	
	//To be called during bootup
	public static void restorePastNotificationStates(Context context, 
								  		  	  		 int trigId, 
								  		  	  		 String notifDesc, 
								  		  	  		 long timeStamp) {
		
		NotifDesc desc = new NotifDesc();
		if(!desc.loadString(notifDesc)) {
			return;
		}
		
		//Cancel all the current alarms
		cancelAllAlarms(context, trigId);
		
		//if it hasn't expired yet, create a notif for the remaining time
		long now = System.currentTimeMillis();
		
		if(timeStamp > now || timeStamp < 0) {
			//TODO log
			return;
		}
		
		//Calculate the elapsed number of minutes for this trigger
		int elapsedMins = (int) (((now - timeStamp) / 1000 ) / 60);
		//Calculate the remaining duration for this trigger
		int remDuration = desc.getDuration() - elapsedMins;
		
		if(remDuration <= 0) {
			//The trigger expired
			return;
		}
		
		//Set an expire alarm for the remaining duration
		setAlarm(context, ACTION_EXPIRE_ALM, trigId, 
				 remDuration, null);
		
		//Set an alarm for the remaining repeats, if any
		List<Integer> repeats = desc.getSortedRepeats();	
		//Check if there is any repeat after the current time
		//Older repeats are to be discarded
		int i = 0;
		for(int repeat : repeats) {
			
			if(repeat > elapsedMins) {
				
				//There are repeats after the current time
				
				//Discard the older ones from the list
				int[] repeatDiffs = getRepeatDiffs(repeats.subList(i, repeats.size()));
				
				/* Subtract the elapsed time from the first repeat
				 * For instance, let the original repeat list be [5, 10, 15].
				 * Let's assume 7 minutes have elapsed. 
				 * So, the remaining list would be [10, 15] and the diff list 
				 * of this remaining list would be [10, 5]. Now, since 7 minutes
				 * have already elapsed, the first alarm should be set to fire
				 * after 3 minutes (10 - 7)
				 */
				
				repeatDiffs[0] -=  elapsedMins;
				
				setRepeatAlarm(context, trigId, repeatDiffs);
				break;
			}
			
			i++;
		}
	}
	
	private static int[] getRepeatDiffs(List<Integer> repeatList) {
		
		int[] ret = new int[repeatList.size()];
		
		int i = 0;
		for(int repeat : repeatList) {
			
			if(i > 0) {
				ret[i] = (repeat - ret[i-1]);
			}
			else {
				ret[i] = repeat;
			}
			
			i++;
		}
		
		return ret;
	}
	
	private static void repeatReminder(Context context, int trigId, Intent intent) {
		
		Set<String> actSurveys = NotifSurveyAdaptor.getActiveSurveysForTrigger(context, 
																			   trigId);
		
		//Check if this trigger is still active. If not cancel all the alarms
		if(actSurveys.size() == 0) {
			cancelAllAlarms(context, trigId);
			return;
		}
		
		//Trigger is still active, alert the user
		refreshNotification(context, false);
		//Continue the remaining repeat reminders
		int[] repeatDiffs = intent.getIntArrayExtra(KEY_REPEAT_LIST);
		setRepeatAlarm(context, trigId, repeatDiffs);
	}
	
	private static void handleNotifClicked(Context context) {
		//Hide the notification window when the user clicks on it
		hideNotification(context);
		
		//Broadcast to Andwellness
		context.sendBroadcast(new Intent(ACTION_TRIGGER_NOTIFICATION));
	}
	
	private static void saveNotifVisibility(Context context, boolean visible) {
		SharedPreferences pref = context.getSharedPreferences(
										 Notifier.class.getName(), 
										 Context.MODE_PRIVATE);
		
		SharedPreferences.Editor editor = pref.edit();
		editor.putBoolean(KEY_NOTIF_VISIBILITY_PREF, visible);
		editor.commit();
	}
	
	private static boolean getNotifVisibility(Context context) {
		SharedPreferences pref = context.getSharedPreferences(
										 Notifier.class.getName(), 
										 Context.MODE_PRIVATE);
		
		return pref.getBoolean(KEY_NOTIF_VISIBILITY_PREF, false);
	}
	
	private static void handleNotifDeleted(Context context) {
		saveNotifVisibility(context, false);
	}
	
	private static void handleTriggerExpired(Context context, int trigId) {
	
		Log.i(DEBUG_TAG, "Notifier: Handling expiration alarm for: " 
				+ trigId);
		
		//Log information related to expired triggers.
		NotifSurveyAdaptor.handleExpiredTrigger(context, trigId);
		
		//Quietly refresh the notification
		Notifier.refreshNotification(context, true);
	}
	
	public static void notifyNewTrigger(Context context, 
							  		 	int trigId, 
							  		 	String notifDesc) {

		//Clear all existing alarms for this trigger if required
		cancelAllAlarms(context, trigId);
		//Update the notification with quite = false
		refreshNotification(context, false);
		
		NotifDesc desc = new NotifDesc();
		if(!desc.loadString(notifDesc)) {	
			//TODO log
			return;
		}
		
		//Set an alarm to expire this trigger notif
		setAlarm(context, ACTION_EXPIRE_ALM, trigId, 
						desc.getDuration(), null);
		
		//Set an alarm for repeat reminder
		int[] repeatDiffs = getRepeatDiffs(desc.getSortedRepeats());
		setRepeatAlarm(context, trigId, repeatDiffs);
	}	

	public static void removeTriggerNotification(Context context, int trigId) {
		//Clear all existing alarms for this trigger if required
		cancelAllAlarms(context, trigId);
		refreshNotification(context, true);
	}
	
	public static class NotifReceiver extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			
			if(intent.getAction().equals(ACTION_NOTIF_CLICKED)) {
				Notifier.handleNotifClicked(context);
			}
			else if(intent.getAction().equals(ACTION_NOTIF_DELETED)) {
				Notifier.handleNotifDeleted(context);
			}
			else if(intent.getAction().equals(ACTION_EXPIRE_ALM)) {
				
				Notifier.handleTriggerExpired(context, 
						intent.getIntExtra(KEY_TRIGGER_ID, -1));
			}
			else if(intent.getAction().equals(ACTION_REPEAT_ALM)) {
				
				if(!intent.hasExtra(KEY_TRIGGER_ID)) {
					return;
				}
				
				int trigId = intent.getIntExtra(KEY_TRIGGER_ID, -1);
				
				Notifier.repeatReminder(context, trigId, intent);
			}
		}

	}
}
