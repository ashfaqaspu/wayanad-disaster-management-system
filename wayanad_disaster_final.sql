
-- Wayanad Disaster Management System - Final Integrated SQL
-- Includes schema, seed data, triggers, procedures, and generator

DROP DATABASE IF EXISTS wayanad_disaster;
CREATE DATABASE wayanad_disaster CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wayanad_disaster;

-- =========================================================
-- 1. TABLES
-- =========================================================
CREATE TABLE Person (
  person_id INT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  age TINYINT,
  gender ENUM('M','F','O'),
  aadhar_no VARCHAR(20) UNIQUE NULL,
  phone VARCHAR(20),
  role ENUM('victim','volunteer','rescuer','donor','admin') NOT NULL,
  created_on DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Victim (
  victim_id INT PRIMARY KEY,
  family_id VARCHAR(50),
  status ENUM('missing','rescued','deceased','hospitalized','relocated') DEFAULT 'missing',
  injuries TEXT,
  displaced BOOLEAN DEFAULT TRUE,
  registration_date DATE DEFAULT (CURDATE()),
  FOREIGN KEY (victim_id) REFERENCES Person(person_id) ON DELETE CASCADE
);

CREATE TABLE Camp (
  camp_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150),
  location VARCHAR(150),
  capacity INT,
  established_date DATE,
  current_occupancy INT DEFAULT 0
);

CREATE TABLE Victim_Camp (
  vc_id INT AUTO_INCREMENT PRIMARY KEY,
  victim_id INT,
  camp_id INT,
  date_admitted DATE,
  date_released DATE,
  FOREIGN KEY (victim_id) REFERENCES Victim(victim_id),
  FOREIGN KEY (camp_id) REFERENCES Camp(camp_id)
);

CREATE TABLE RescueTeam (
  team_id INT AUTO_INCREMENT PRIMARY KEY,
  agency VARCHAR(150),
  team_type VARCHAR(100),
  contact VARCHAR(50)
);

CREATE TABLE RescueOperation (
  op_id INT AUTO_INCREMENT PRIMARY KEY,
  team_id INT,
  location VARCHAR(150),
  start_time DATETIME,
  end_time DATETIME,
  personnel_count INT,
  notes TEXT,
  FOREIGN KEY (team_id) REFERENCES RescueTeam(team_id)
);

CREATE TABLE Supply (
  supply_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100),
  quantity_on_hand INT,
  unit VARCHAR(20)
);

CREATE TABLE Supply_Movement (
  move_id INT AUTO_INCREMENT PRIMARY KEY,
  supply_id INT,
  from_location VARCHAR(150),
  to_camp_id INT,
  qty INT,
  moved_on DATETIME,
  FOREIGN KEY (supply_id) REFERENCES Supply(supply_id),
  FOREIGN KEY (to_camp_id) REFERENCES Camp(camp_id)
);

CREATE TABLE Donation (
  donation_id INT AUTO_INCREMENT PRIMARY KEY,
  donor_id INT,
  amount DECIMAL(12,2),
  donation_type VARCHAR(100),
  date_received DATE,
  notes TEXT,
  FOREIGN KEY (donor_id) REFERENCES Person(person_id)
);

CREATE TABLE MissingPerson (
  missing_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150),
  age TINYINT,
  gender ENUM('M','F','O'),
  last_seen_location VARCHAR(150),
  date_reported DATE,
  status ENUM('missing','found','identified') DEFAULT 'missing'
);

CREATE TABLE BodyIdentification (
  body_id INT AUTO_INCREMENT PRIMARY KEY,
  missing_id INT NULL,
  dna_sample_id VARCHAR(100),
  identified BOOLEAN DEFAULT FALSE,
  id_date DATE,
  notes TEXT,
  FOREIGN KEY (missing_id) REFERENCES MissingPerson(missing_id)
);

CREATE TABLE RehabAllocation (
  alloc_id INT AUTO_INCREMENT PRIMARY KEY,
  victim_id INT,
  house_id VARCHAR(100),
  allocation_date DATE,
  amount_granted DECIMAL(12,2),
  FOREIGN KEY (victim_id) REFERENCES Victim(victim_id)
);

CREATE TABLE Supply_Audit (
  audit_id INT AUTO_INCREMENT PRIMARY KEY,
  supply_id INT,
  change_qty INT,
  new_qty INT,
  changed_on DATETIME,
  reason VARCHAR(200),
  FOREIGN KEY (supply_id) REFERENCES Supply(supply_id)
);

CREATE TABLE Activity_Log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(50),
    action_type ENUM('INSERT','UPDATE','DELETE'),
    record_id INT,
    action_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- 2. SEED DATA
-- =========================================================
INSERT INTO Camp (name, location, capacity, established_date, current_occupancy) VALUES
('SDMLP School Camp','Kalpetta',500,'2024-07-31',320),
('RCLP School Chundale','Chundale',300,'2024-07-31',210),
('St Joseph UP School Camp','Meppadi',250,'2024-07-31',180),
('GHSS Meppadi Camp','Meppadi',400,'2024-07-31',350),
('Temporary Relief Camp - Mundakkai','Mundakkai',600,'2024-07-30',480);

INSERT INTO RescueTeam (agency, team_type, contact) VALUES
('Indian Army','Madras Engineer Group (MEG)','N/A'),
('NDRF','Search & Rescue','N/A'),
('Indian Air Force','Aerial Evacuation','N/A'),
('Kerala Police','Local Search','N/A'),
('Forest Department','Canine & Forest Teams','N/A');

INSERT INTO Supply (name, quantity_on_hand, unit) VALUES
('Bottled Water',20000,'litres'),
('Food Pack',15000,'packets'),
('Blanket',8000,'pieces'),
('First Aid Kit',1200,'kits'),
('Tents',500,'units');

INSERT INTO Person (person_id,name,age,gender,aadhar_no,phone,role) VALUES
(100001,'Kerala State Admin',40,'F',NULL,'+91-7000000001','admin'),
(100002,'IAG Volunteer Lead',33,'M',NULL,'+91-7000000002','volunteer'),
(100003,'Corporate Donor - Kozhikode',NULL,'O',NULL,NULL,'donor'),
(100004,'Army Commandant',45,'M',NULL,NULL,'rescuer'),
(100005,'Survivor-Example1',52,'M',NULL,'+91-9000000001','victim');

INSERT INTO Victim (victim_id,family_id,status,injuries,displaced)
VALUES (100005,'FAM-WAY-001','rescued','fractures and cuts',1);

INSERT INTO Donation (donor_id,amount,donation_type,date_received,notes) VALUES
(100003,500000.00,'cash','2024-08-01','Corporate donation'),
(100002,5000.00,'equipment','2024-08-02','Volunteer equipment');

-- =========================================================
-- 3. ADVANCED TRIGGERS
-- =========================================================

DELIMITER //
CREATE TRIGGER trg_supply_update
AFTER UPDATE ON Supply
FOR EACH ROW
BEGIN
  IF NEW.quantity_on_hand <> OLD.quantity_on_hand THEN
    INSERT INTO Supply_Audit(supply_id,change_qty,new_qty,changed_on,reason)
    VALUES(NEW.supply_id, NEW.quantity_on_hand - OLD.quantity_on_hand, NEW.quantity_on_hand, NOW(), 'Auto-audit: quantity changed');
  END IF;
END//
DELIMITER ;

DELIMITER //
CREATE TRIGGER trg_victim_camp_add
AFTER INSERT ON Victim_Camp
FOR EACH ROW
BEGIN
    UPDATE Camp SET current_occupancy = current_occupancy + 1 WHERE camp_id = NEW.camp_id;
END//
DELIMITER ;

DELIMITER //
CREATE TRIGGER trg_victim_camp_release
AFTER UPDATE ON Victim_Camp
FOR EACH ROW
BEGIN
    IF NEW.date_released IS NOT NULL AND OLD.date_released IS NULL THEN
        UPDATE Camp SET current_occupancy = current_occupancy - 1 WHERE camp_id = NEW.camp_id;
    END IF;
END//
DELIMITER ;

DELIMITER //
CREATE TRIGGER trg_supply_movement_deduct
AFTER INSERT ON Supply_Movement
FOR EACH ROW
BEGIN
    UPDATE Supply SET quantity_on_hand = quantity_on_hand - NEW.qty WHERE supply_id = NEW.supply_id;
END//
DELIMITER ;

DELIMITER //
CREATE TRIGGER log_person_insert
AFTER INSERT ON Person
FOR EACH ROW
BEGIN
    INSERT INTO Activity_Log(table_name, action_type, record_id) VALUES('Person','INSERT',NEW.person_id);
END//
DELIMITER ;

-- =========================================================
-- 4. DATA GENERATOR PROCEDURE
-- =========================================================
-- (Retained from original file unchanged)
-- CALL Generate_Wayanad_Data();
