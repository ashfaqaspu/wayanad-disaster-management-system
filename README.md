Wayanad Disaster Management System

A complete database + GUI application designed for managing disaster response operations in Wayanad District.
The system integrates victim tracking, camp management, supply movement, rescue operations, error logging, and statistics dashboards in one unified architecture.

🖥️ System Screenshots
1. Transfer Error Log (Safe Trigger Demo)

Shows auto-adjustment of supply transfer requests when requested quantity exceeds availability.
Useful for safety, transparency, and audit tracking.

2. Statistics Dashboard

Overview of real-time disaster summaries:
Victims, camps, rescue teams, donations, missing persons, rehab allocations.

3. Person Table Viewer

Admin GUI to view, search, add, update, and delete records.

4. Main Menu Interface

Central UI hub for navigating all modules:
Victims, camps, supply movements, rescue operations, audits, etc.

5. ER Diagram (Full Conceptual Model)
   https://github.com/ashfaqaspu/wayanad-disaster-management-system/blob/master/schema.jpg

📌 Overview

The Wayanad Disaster Management System is a fully-featured MySQL-backed disaster response database with a Java GUI frontend. It is built to support:

Real-time camp occupancy updates

Safe stock transfers with auto-correction

Missing person tracking

Rescue team and operation coordination

Rehab allocation management

Donation and donor handling

Automatic audit trails

Error logging for invalid operations

Ideal for NGOs, District Collectorate, Emergency Cells, and academic DBMS projects.

🚨 Key Features
🔹Safety Automation

Supply transfers auto-adjust to prevent negative quantities

All mismatches logged in transfer_error_log

All stock changes logged in supply_audit

🔹Data Management

Victim registration & camp assignment

Missing persons & body identification

Rescue team operations

Donations & donors

Rehab allocation after disaster

🔹GUI Application

Table viewer (read/update/delete)

Statistics dashboard

Module-based navigation

Admin-friendly interface

🔹Database Intelligence

Stored procedures for dataset generation

Triggers for auto-updating camp capacity

Triggers for stock safety

Complete ER diagram

Strict foreign key relationships

🗂️ Project Structure
📦 wayanad-disaster-management-system
 ├── database/
 │   ├── final_wayanad_full_dataset_with_safe_triggers.sql
 │   ├── stored_procedures.sql
 │   ├── triggers.sql
 │   └── ER_diagram.puml
 ├── app/
 │   ├── screenshots/
 │   ├── MainMenu.java
 │   ├── TableViewer.java
 │   ├── StatisticsDashboard.java
 │   └── modules/
 ├── README.md
 └── LICENSE

🛠️ Technologies Used

MySQL 8.0+

Java Swing GUI

JDBC

PlantUML (ER diagrams)

Triggers, Procedures, Audits

📥 How to Run
1. Import the SQL database
mysql -u root -p < final_wayanad_full_dataset_with_safe_triggers.sql

2. Compile & run Java application
javac MainMenu.java
java MainMenu

🧩 Future Enhancements

Web dashboard

REST API with Node/Flask

GIS mapping

SMS alert integration

AI-based risk forecasting

🤝 Contributing

Pull requests are welcome.
For major changes, please open an issue first.

📧 Contact

Abdul khader Ashfaq k a
Developer — DBMS Disaster Management System
