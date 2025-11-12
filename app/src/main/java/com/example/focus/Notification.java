package com.example.focus;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

// This is a data "model" class to hold notification data from Firestore
public class Notification {
    private String studentName;
    private String message;
    private @ServerTimestamp Date timestamp;
    private String studentId;
    private String parentId;
    private boolean read; // To track if the badge should be shown

    // Required empty constructor for Firestore
    public Notification() {}

    public String getStudentName() { return studentName; }
    public String getMessage() { return message; }
    public Date getTimestamp() { return timestamp; }
    public String getStudentId() { return studentId; }
    public String getParentId() { return parentId; }
    public boolean isRead() { return read; }

    // We don't need setters, Firestore sets them directly
}