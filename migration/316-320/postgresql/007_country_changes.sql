-- juddm BugFix:(Country Changes) 

--add Serbian currency
INSERT INTO C_CURRENCY
			(C_CURRENCY_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY, ISO_CODE,
			CURSYMBOL, DESCRIPTION, STDPRECISION, COSTINGPRECISION, ISEURO, ISEMUMEMBER)
		VALUES(347, 0, 0, 'Y', TO_TIMESTAMP('2003-08-06','YYYY-MM-DD'),0, TO_TIMESTAMP('2000-01-02','YYYY-MM-DD'), 0, 'RSD',
			'RSD', 'Serbian Dinar', 2, 4, 'N', 'N');

-- add country Serbia
INSERT INTO C_COUNTRY
		(C_COUNTRY_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY,
		NAME, DESCRIPTION, COUNTRYCODE, HASREGION, DISPLAYSEQUENCE, HASPOSTAL_ADD, C_CURRENCY_ID,
		ISADDRESSLINESREVERSE, ISADDRESSLINESLOCALREVERSE)
	VALUES (349, 0, 0, 'Y', TO_TIMESTAMP('2003-03-09', 'YYYY-MM-DD'), 0, TO_TIMESTAMP('2000-01-02','YYYY-MM-DD'), 0,
		'Serbia', 'Serbia', 'RS', 'N', '@C@, @P@', 'N', 347, 'N', 'N');

-- add country Montenegro		
INSERT INTO C_COUNTRY
		(C_COUNTRY_ID, AD_CLIENT_ID, AD_ORG_ID, ISACTIVE, CREATED, CREATEDBY, UPDATED, UPDATEDBY,
		NAME, DESCRIPTION, COUNTRYCODE, HASREGION, DISPLAYSEQUENCE, HASPOSTAL_ADD, C_CURRENCY_ID,
		ISADDRESSLINESREVERSE, ISADDRESSLINESLOCALREVERSE)
	VALUES (350, 0, 0, 'Y', TO_TIMESTAMP('2003-03-09','YYYY-MM-DD'), 0, TO_TIMESTAMP('2000-01-02','YYYY-MM-DD'), 0,
		'Montenegro', 'Montenegro', 'ME', 'N', '@C@, @P@', 'N', 102, 'N', 'N');

-- deactivate Yugoslavia
UPDATE C_COUNTRY SET ISACTIVE = 'N' WHERE C_COUNTRY_ID=346;

-- deactivate Yugoslavia currency
UPDATE C_CURRENCY SET ISACTIVE = 'N' WHERE C_CURRENCY_ID = 314;

COMMIT;
