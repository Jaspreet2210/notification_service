# Reliable Multi-Channel Notification Service

Welcome! This is a beginner-friendly backend service built with **Java** and the **Spring Boot** framework. It acts as a smart engine to send notifications across three channels: **Email, SMS, and In-App Push**. 

It is engineered with "reliability-first" principles, meaning it is designed to **never lose a notification** and **never send a duplicate notification**, even if external networks go down or the server suddenly crashes.

To make this fun and easy to test, the project includes an **interactive dark-mode dashboard** that updates in real-time as notifications are sent, retried, or failed.

---

## 🚀 Features Included

*   **Multi-Channel Support:** Dynamically routes messages to Email, SMS, or In-App destinations.
*   **Real-Time Dashboard:** Open `http://localhost:8080` to see a premium dashboard showing live analytics, status counters, and a scrolling activity terminal.
*   **Duplicate Prevention (Idempotency):** Blocks duplicate submissions using an "Idempotency Key," ensuring users never receive spam.
*   **Self-Healing Retries:** If a temporary error occurs (like a bad network), the system automatically schedules a retry in the future using **exponential backoff** (waiting longer and longer between attempts: 1s, 2s, 4s...).
*   **Outage Sandbox Simulator:** Toggle the "Simulate Outage" switch on the dashboard to artificially shut down external providers and watch the retry engine wake up live!

---

## 🛠️ Requirements & Setup

Before starting, make sure you have:
1.  **Java SDK 21** or higher installed.
2.  **Apache Maven** installed (to compile and run).

### How to Run the Application

Open your terminal in this project directory and run:

```bash
mvn spring-boot:run
```

Once the terminal prints `Engine Online` and shows startup logs, you are ready to explore!

---

## 🎮 How to Test the Project (Step-by-Step)

Open your web browser and navigate to: **`http://localhost:8080`**

### Step 1: Send a Normal Notification
1.  On the **Dispatch Notification** form, select **Email**.
2.  Type a recipient email (e.g., `test@example.com`), a title, and a message.
3.  Click **Dispatch Asynchronously**.
4.  *Result:* The notification is instantly registered in the database, picked up by a background worker thread, and successfully dispatched. You will see the event pop up in the terminal console and the table at the bottom of the page!

### Step 2: Test Double-Send Protection (Idempotency)
1.  Look at the **Idempotency Key** field in the form.
2.  **Uncheck** the box that says *"Rotate key automatically after sending"*.
3.  Click **Dispatch Asynchronously**. The message is sent successfully.
4.  Click **Dispatch Asynchronously** a *second* time (keeping the exact same key in the box).
5.  *Result:* The console logs show:
    `[Idempotency] 200 OK (CACHED). Key already processed!`
    The system intercepted the double-send and safely blocked a duplicate email from going out!

### Step 3: Test Provider Outages & Auto-Retries
1.  In the **Resilience Sandbox** card, toggle **Simulate Provider Outage** to **ON**.
2.  Send a new notification.
3.  *Result:* You will see the notification status set to `RETRYING` (marked as orange pending badge). 
4.  The system tries to send, encounters the mock outage, and schedules retries with exponential backoffs (1 second, then 2 seconds, then 4 seconds).
5.  Toggle **Simulate Provider Outage** back to **OFF**.
6.  *Result:* On the next scheduled background retry, the system successfully connects and delivers the notification, transitioning its status badge to a glowing green `SUCCESS`!

---

## 📂 Code Map: Where is everything?

Here is a quick directory guide for beginners to navigate the codebase:

*   **`src/main/resources/static/`** (The Frontend)
    *   `index.html`: The HTML layout for the dashboard.
    *   `style.css`: The modern glassmorphic theme and neon glow badges.
    *   `app.js`: The JavaScript that hooks up the submit button to the API and listens for real-time Server-Sent Events (SSE).
*   **`src/main/java/com/example/notification/`** (The Backend)
    *   `NotificationApplication.java`: The main entry point that starts Spring Boot.
    *   `controller/NotificationController.java`: The REST API layer exposing `/notifications` and SSE `/stream`.
    *   `model/Notification.java`: The database table structure mapping notification details.
    *   `service/NotificationService.java`: The orchestrator handling timeouts, retry intervals, and status changes.
    *   `queue/NotificationProcessor.java`: Holds the thread pool executing notification workers concurrently in the background.

---

## 💾 How to inspect the H2 Database

You can inspect the live database tables in your browser while the application is running:
1.  Navigate to: `http://localhost:8080/h2-console`
2.  Change the **JDBC URL** to: `jdbc:h2:mem:notificationdb`
3.  Set the **User Name** to: `sa` (leave the password blank).
4.  Click **Connect**.
5.  Double-click `NOTIFICATIONS` on the left and run a query to see your sent history!
