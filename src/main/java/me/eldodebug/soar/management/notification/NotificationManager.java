package me.eldodebug.soar.management.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.language.TranslateText;

public class NotificationManager {

	private static final int MAX_HISTORY = 60;

	private LinkedBlockingQueue<Notification> notifications = new LinkedBlockingQueue<Notification>();
	private final ArrayList<NotificationRecord> history = new ArrayList<NotificationRecord>();
	private int unread;
	
	public NotificationManager() {
		Glide.getInstance().getEventManager().register(new NotificationHandler(notifications));
	}
	
	public void post(TranslateText title, TranslateText message, NotificationType type) {
		post(title.getText(), message.getText(), type);
	}
	public void post(String title, String message, NotificationType type) {
		notifications.add(new Notification(title, message, type));
		record(title, message, type);
	}
	
	private synchronized void record(String title, String message, NotificationType type) {
		
		history.add(0, new NotificationRecord(title, message, type, System.currentTimeMillis()));
		
		while(history.size() > MAX_HISTORY) {
			history.remove(history.size() - 1);
		}
		
		unread++;
	}
	
	public synchronized List<NotificationRecord> getHistory() {
		return Collections.unmodifiableList(new ArrayList<NotificationRecord>(history));
	}
	
	public synchronized int getUnread() {
		return unread;
	}
	
	public synchronized void markAllRead() {
		unread = 0;
	}
	
	public synchronized void clearHistory() {
		history.clear();
		unread = 0;
	}
	
	/**
	 * A plain data snapshot, so the overlay never holds on to a transient toast.
	 */
	public static class NotificationRecord {
		
		private final String title, message;
		private final NotificationType type;
		private final long timestamp;
		
		public NotificationRecord(String title, String message, NotificationType type, long timestamp) {
			this.title = title;
			this.message = message;
			this.type = type;
			this.timestamp = timestamp;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getMessage() {
			return message;
		}
		
		public NotificationType getType() {
			return type;
		}
		
		public long getTimestamp() {
			return timestamp;
		}
	}
}
