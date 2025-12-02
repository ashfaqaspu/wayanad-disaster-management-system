-- final_wayanad_full_dataset_with_safe_triggers.sql
-- Full schema + safe triggers + procedures + generator (~500+ rows)
-- MySQL 8.0+ compatible
-- Option B behaviour: supply movement auto-adjusted to available qty and logged.

DROP DATABASE IF EXISTS wayanad_disaster;
CREATE DATABASE wayanad_disaster CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wayanad_disaster;

-- =========================================================
-- 1) TABLES
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
  quantity_on_hand INT DEFAULT 0,
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

-- Table to log auto-adjusted transfers (Option B)
CREATE TABLE Transfer_Error_Log (
  log_id INT AUTO_INCREMENT PRIMARY KEY,
  supply_id INT,
  requested_qty INT,
  adjusted_qty INT,
  message VARCHAR(255),
  log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (supply_id) REFERENCES Supply(supply_id)
);

-- =========================================================
-- 2) SEED DATA (small seeds from report)
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
-- 3) TRIGGERS (safe versions + logs)
-- =========================================================
DELIMITER //

-- 3.1 Log Person insert into Activity_Log
CREATE TRIGGER trg_log_person_insert
AFTER INSERT ON Person
FOR EACH ROW
BEGIN
  INSERT INTO Activity_Log(table_name, action_type, record_id, action_timestamp)
  VALUES ('Person', 'INSERT', NEW.person_id, NOW());
END//

-- 3.2 When victim added to a camp -> increase occupancy (never below 0)
CREATE TRIGGER trg_victim_camp_add
AFTER INSERT ON Victim_Camp
FOR EACH ROW
BEGIN
  UPDATE Camp
  SET current_occupancy = LEAST(current_occupancy + 1, capacity)
  WHERE camp_id = NEW.camp_id;
END//

-- 3.3 When victim released -> decrease occupancy but not below 0
CREATE TRIGGER trg_victim_camp_release
AFTER UPDATE ON Victim_Camp
FOR EACH ROW
BEGIN
  IF NEW.date_released IS NOT NULL AND OLD.date_released IS NULL THEN
    UPDATE Camp
    SET current_occupancy = GREATEST(current_occupancy - 1, 0)
    WHERE camp_id = NEW.camp_id;
  END IF;
END//

-- 3.4 BEFORE INSERT on Supply_Movement: Option B behaviour
-- If requested qty > available, adjust NEW.qty to available and log adjustment
CREATE TRIGGER trg_supply_movement_before_insert_adjust
BEFORE INSERT ON Supply_Movement
FOR EACH ROW
BEGIN
  DECLARE avail INT DEFAULT 0;
  -- get current available quantity (use FOR UPDATE to lock row in transactional contexts)
  SELECT quantity_on_hand INTO avail FROM Supply WHERE supply_id = NEW.supply_id FOR UPDATE;
  IF avail IS NULL THEN
    SET avail = 0;
  END IF;

  IF NEW.qty IS NULL OR NEW.qty <= 0 THEN
    -- ensure positive qty; if none provided, set to 1 (or you can set to 0)
    SET NEW.qty = 1;
  END IF;

  IF NEW.qty > avail THEN
    -- adjust to available (may be 0)
    INSERT INTO Transfer_Error_Log(supply_id, requested_qty, adjusted_qty, message)
    VALUES (NEW.supply_id, NEW.qty, GREATEST(avail,0),
            CONCAT('Requested ', NEW.qty, ' but only ', GREATEST(avail,0), ' available; adjusted.'));
    SET NEW.qty = GREATEST(avail, 0);
  END IF;

  -- Ensure moved_on is set
  IF NEW.moved_on IS NULL THEN
    SET NEW.moved_on = NOW();
  END IF;
END//

-- 3.5 AFTER INSERT on Supply_Movement: deduct inventory safely (never negative)
CREATE TRIGGER trg_supply_movement_deduct
AFTER INSERT ON Supply_Movement
FOR EACH ROW
BEGIN
  IF NEW.qty > 0 THEN
    UPDATE Supply
    SET quantity_on_hand = GREATEST(quantity_on_hand - NEW.qty, 0)
    WHERE supply_id = NEW.supply_id;
  END IF;
END//

-- 3.6 Supply updates -> audit changes (safe)
CREATE TRIGGER trg_supply_update_audit
AFTER UPDATE ON Supply
FOR EACH ROW
BEGIN
  IF NEW.quantity_on_hand <> OLD.quantity_on_hand THEN
    INSERT INTO Supply_Audit(supply_id, change_qty, new_qty, changed_on, reason)
    VALUES (NEW.supply_id, NEW.quantity_on_hand - OLD.quantity_on_hand, NEW.quantity_on_hand, NOW(), 'Auto Audit: Quantity adjusted');
  END IF;
END//

DELIMITER ;

-- =========================================================
-- 4) PROCEDURES
-- =========================================================
DELIMITER //

-- 4.1 Allocate relief to camp procedure (unchanged, safe)
CREATE PROCEDURE AllocateReliefToCamp(IN p_camp_id INT, IN p_amount DECIMAL(12,2))
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE v_victim INT;
  DECLARE cur CURSOR FOR
    SELECT v.victim_id FROM Victim v
    JOIN Victim_Camp vc ON v.victim_id = vc.victim_id
    WHERE vc.camp_id = p_camp_id;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_victim;
    IF done THEN
      LEAVE read_loop;
    END IF;
    INSERT INTO RehabAllocation(victim_id, house_id, allocation_date, amount_granted)
    VALUES (v_victim, NULL, CURDATE(), p_amount);
  END LOOP;
  CLOSE cur;
END//

-- 4.2 Generate_Wayanad_Data: safe generator with positive numbers
CREATE PROCEDURE Generate_Wayanad_Data()
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE team_count INT;
  DECLARE supply_count INT;

  -- Persons (600)
  SET i = 200000;
  WHILE i < 200800 DO
    INSERT INTO Person(person_id,name,age,gender,aadhar_no,phone,role)
    VALUES(i,
           CONCAT('Person_',i),
           FLOOR(18 + (RAND()*60)),
           ELT(FLOOR(RAND()*3)+1,'M','F','O'),
           CONCAT('A',LPAD(i,9,'0')),
           CONCAT('+91',LPAD(i,10,'0')),
           ELT(FLOOR(RAND()*5)+1,'victim','volunteer','rescuer','donor','admin'));
    SET i = i + 1;
  END WHILE;

  -- Victims (~400)
  SET i = 200000;
  WHILE i < 200600 DO
    IF RAND() < 0.7 THEN
      INSERT INTO Victim(victim_id,family_id,status,injuries,displaced,registration_date)
      VALUES(i,
             CONCAT('F-',LPAD(i,6,'0')),
             ELT(FLOOR(RAND()*5)+1,'missing','rescued','deceased','hospitalized','relocated'),
             CASE WHEN RAND()<0.4 THEN 'minor injuries'
                  WHEN RAND()<0.6 THEN 'fracture' ELSE NULL END,
             TRUE,
             DATE_SUB(CURDATE(),INTERVAL FLOOR(RAND()*30) DAY));
    END IF;
    SET i = i + 1;
  END WHILE;

  -- Extra camps (~60)
  SET i = 1;
  WHILE i <= 60 DO
    INSERT INTO Camp(name,location,capacity,established_date,current_occupancy)
    VALUES(CONCAT('Relief Camp ',i),
           ELT(FLOOR(RAND()*8)+1,'Kalpetta','Meppadi','Mundakkai','Chooralmala','Attamala','Chundale','Kozhikode','Sulthan Bathery'),
           FLOOR(150 + RAND()*300),
           DATE_SUB('2024-07-31',INTERVAL FLOOR(RAND()*10) DAY),
           FLOOR(50 + RAND()*150));
    SET i = i + 1;
  END WHILE;

  -- Rescue teams + operations
  INSERT INTO RescueTeam(agency,team_type,contact) VALUES
  ('Odisha Rescue','NDRF-12','N/A'),
  ('Local Volunteers','Community','N/A'),
  ('NGO - ReliefOrg','Medical','N/A'),
  ('IAG Group','Coordination','N/A');

  SET team_count = (SELECT COUNT(*) FROM RescueTeam);

  SET i = 1;
  WHILE i <= 300 DO
    INSERT INTO RescueOperation(team_id,location,start_time,end_time,personnel_count,notes)
    VALUES(FLOOR(1 + (RAND()*team_count)),
           ELT(FLOOR(RAND()*8)+1,'Mundakkai','Chooralmala','Punjirimattom','Vellarmala','Attamala','Meppadi','Kalpetta','Chundale'),
           DATE_ADD('2024-07-31 00:00:00',INTERVAL FLOOR(RAND()*10) HOUR),
           DATE_ADD('2024-07-31 02:00:00',INTERVAL FLOOR(RAND()*200) HOUR),
           FLOOR(10 + RAND()*90),
           CONCAT('Operation auto-generated #',i));
    SET i = i + 1;
  END WHILE;

  -- Extra supplies
  INSERT INTO Supply(name,quantity_on_hand,unit) VALUES
  ('Sanitary Kits',5000,'sets'),
  ('Baby Food',2000,'packs'),
  ('Cooking Gas Cylinder',200,'units'),
  ('Clothes',8000,'pieces'),
  ('Solar Lamps',1000,'units');

  SET supply_count = (SELECT COUNT(*) FROM Supply);

  -- Safe supply movements
  SET i = 1;
  WHILE i <= 400 DO
    INSERT INTO Supply_Movement(supply_id,from_location,to_camp_id,qty,moved_on)
    VALUES(FLOOR(1 + RAND()*supply_count),
           ELT(FLOOR(RAND()*6)+1,'Central Warehouse','Airport','Railhead','Local NGO Depot','Govt Storage','Donor Warehouse'),
           FLOOR(1 + RAND()*60),
           FLOOR(5 + RAND()*55),
           DATE_ADD('2024-07-31 00:00:00',INTERVAL FLOOR(RAND()*20) HOUR));
    SET i = i + 1;
  END WHILE;

  -- Donations & donors
  INSERT INTO Person(person_id,name,age,gender,aadhar_no,phone,role)
  VALUES(300000,'State Govt Relief Fund',NULL,'O',NULL,NULL,'donor');

  SET i = 1000;
  WHILE i <= 1300 DO
    INSERT INTO Person(person_id,name,age,gender,aadhar_no,phone,role)
    VALUES(i+300000,CONCAT('Donor_',i),NULL,ELT(FLOOR(RAND()*3)+1,'M','F','O'),NULL,CONCAT('+91',LPAD(i+300000,10,'0')),'donor');
    INSERT INTO Donation(donor_id,amount,donation_type,date_received,notes)
    VALUES(i+300000,ROUND(1000 + RAND()*50000,2),
           ELT(FLOOR(RAND()*3)+1,'cash','in-kind','food'),
           DATE_ADD('2024-07-31',INTERVAL FLOOR(RAND()*30) DAY),
           CONCAT('Auto donation ',i));
    SET i = i + 1;
  END WHILE;

  -- Missing persons & body IDs
  SET i = 1;
  WHILE i <= 200 DO
    INSERT INTO MissingPerson(name,age,gender,last_seen_location,date_reported,status)
    VALUES(CONCAT('Missing_',i),
           FLOOR(5 + RAND()*70),
           ELT(FLOOR(RAND()*3)+1,'M','F','O'),
           ELT(FLOOR(RAND()*8)+1,'Mundakkai','Chooralmala','Punjirimattom','Attamala','Vellarmala','Meppadi','Kalpetta','Chundale'),
           DATE_ADD('2024-07-30',INTERVAL FLOOR(RAND()*10) DAY),
           ELT(FLOOR(RAND()*3)+1,'missing','found','identified'));
    IF RAND() < 0.30 THEN
      INSERT INTO BodyIdentification(missing_id,dna_sample_id,identified,id_date,notes)
      VALUES(LAST_INSERT_ID(),
             CONCAT('DNA-',FLOOR(RAND()*1000000)),
             IF(RAND()<0.6,TRUE,FALSE),
             DATE_ADD('2024-08-02',INTERVAL FLOOR(RAND()*30) DAY),
             'Auto-gen body record');
    END IF;
    SET i = i + 1;
  END WHILE;

  -- Victim_Camp links (up to 800 links)
  INSERT INTO Victim_Camp(victim_id,camp_id,date_admitted,date_released)
  SELECT v.victim_id,
         FLOOR(1 + RAND()*60),
         DATE_ADD('2024-07-31',INTERVAL FLOOR(RAND()*10) DAY),
         NULL
  FROM Victim v
  LIMIT 800;

  -- Rehab allocations
  SET i = 1;
  WHILE i <= 300 DO
    INSERT INTO RehabAllocation(victim_id,house_id,allocation_date,amount_granted)
    VALUES(
      (SELECT victim_id FROM Victim ORDER BY RAND() LIMIT 1),
      CONCAT('HOUSE-',LPAD(FLOOR(RAND()*9999),6,'0')),
      DATE_ADD('2024-08-15',INTERVAL FLOOR(RAND()*30) DAY),
      ROUND(20000 + RAND()*50000,2)
    );
    SET i = i + 1;
  END WHILE;

END//
DELIMITER ;

-- =========================================================
-- 5) OPTIONAL: Call generator (uncomment to run)
-- =========================================================
-- CALL Generate_Wayanad_Data();
CALL Generate_Wayanad_Data();

-- =========================================================
-- 6) SAMPLE VALIDATION QUERIES
-- =========================================================
-- SELECT COUNT(*) FROM Person;
-- SELECT COUNT(*) FROM Victim;
-- SELECT COUNT(*) FROM Camp;
-- SELECT SUM(amount) FROM Donation;
-- SELECT * FROM Transfer_Error_Log ORDER BY log_time DESC LIMIT 50;
select * from camp;
select * from supply;
select * from Person;
select * from Victim_Camp;
select * from RescueTeam;
select * from RescueOperation;
select * from Supply_Movement;
select * from Donation;
select * from MissingPerson;
select * from BodyIdentification;
select * from RehabAllocation;
select * from Supply_Audit;

SHOW TRIGGERS;

-- =========================================================
-- 7) SHOW TRIGGERS / END
-- =========================================================

-- You can display triggers with:
-- SHOW TRIGGERS;

-- End of script
