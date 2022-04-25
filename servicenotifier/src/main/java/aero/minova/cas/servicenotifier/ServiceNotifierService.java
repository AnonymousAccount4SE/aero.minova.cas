package aero.minova.cas.servicenotifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import aero.minova.cas.CustomLogger;
import aero.minova.cas.api.domain.Column;
import aero.minova.cas.api.domain.DataType;
import aero.minova.cas.api.domain.OutputType;
import aero.minova.cas.api.domain.Row;
import aero.minova.cas.api.domain.SqlProcedureResult;
import aero.minova.cas.api.domain.Table;
import aero.minova.cas.api.domain.Value;
import aero.minova.cas.controller.SqlProcedureController;
import aero.minova.cas.service.SecurityService;

/*
 * @Service Die Klasse kann später als Cache verwendet werden, wird aber momentan noch nicht benötigt.
 */
public class ServiceNotifierService {

	@Autowired
	SqlProcedureController spc;

	@Autowired
	CustomLogger logger;

	@Autowired
	SecurityService securityService;

	/**
	 * Enthält Tupel aus Prozedurenamen und Tabellennamen. Wird eine der enthaltenen Prozeduren ausgeführt, muss der dazugehörige Dienst angetriggert werden.
	 */
	private Map<String, List<String>> servicenotifier = new HashMap<>();

	/**
	 * Enthält Tupel aus Dienstnamen und Tabellennamen. Wird eine der enthaltenen Tabellen verändert, muss der dazugehörige Dienst angetriggert werden.
	 */
	private Map<String, List<String>> newsfeeds = new HashMap<>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@PostConstruct
	private void setup() {
		spc.registerExtension("registerService", inputTable -> {
			try {
				int keyLong = registerService(inputTable);
				return new ResponseEntity(keyLong, HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("unregisterService", inputTable -> {
			try {
				unregisterService(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("registerProcedureNewsfeed", inputTable -> {
			try {
				registerProcedureNewsfeed(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("unregisterProcedureNewsfeed", inputTable -> {
			try {
				unregisterProcedureNewsfeed(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("unregisterProcedureNewsfeedCompletely", inputTable -> {
			try {
				unregisterProcedureNewsfeedCompletely(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("registerNewsfeedListener", inputTable -> {
			try {
				registerNewsfeedListener(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		spc.registerExtension("unregisterNewsfeedListener", inputTable -> {
			try {
				unregisterNewsfeedListener(inputTable);
				return new ResponseEntity(HttpStatus.ACCEPTED);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Registriert einen Dienst beim CAS und schreibt die dazugehörigen Daten in die xtcasCASServices Tabelle. Die übergebene Table muss dafür in der ersten
	 * Spalte den Namen des Dienstes beinhalten, in der Zweiten die ServiceURL und in der Dritten den Port.
	 * 
	 * @param inputTable
	 *            Die Table, welche in der ersten Spalte den Dienstnamen, in der Zweiten die ServiceURL und in der dritten Column den Port benötigt.
	 * @return Den KeyLong des Dienstes als int.
	 */
	public int registerService(Table inputTable) {
		SqlProcedureResult registerResult = new SqlProcedureResult();

		Table registerSerivceTable = new Table();
		registerSerivceTable.setName("xpcasInsertCASService");
		registerSerivceTable.addColumn(new Column("KeyLong", DataType.INTEGER, OutputType.OUTPUT));
		registerSerivceTable.addColumn(new Column("KeyText", DataType.STRING));
		registerSerivceTable.addColumn(new Column("ServiceURL", DataType.STRING));
		registerSerivceTable.addColumn(new Column("Port", DataType.INTEGER));

		Row registerRow = new Row();
		registerRow.addValue(null);
		registerRow.addValue(inputTable.getRows().get(0).getValues().get(0));
		registerRow.addValue(inputTable.getRows().get(0).getValues().get(1));
		registerRow.addValue(inputTable.getRows().get(0).getValues().get(2));

		registerSerivceTable.addRow(registerRow);
		try {
			registerResult = spc.unsecurelyProcessProcedure(registerSerivceTable);
		} catch (Exception e) {
			logger.logError("The service " + inputTable.getRows().get(0).getValues().get(0).getStringValue() + " could not be registered", e);
			throw new RuntimeException(e);
		}

		return registerResult.getOutputParameters().getRows().get(0).getValues().get(0).getIntegerValue();
	}

	/**
	 * Löscht den Eintrag des Dienstes aus der Datenbank und meldet ihn vom CAS ab.
	 * 
	 * @param inputTable
	 *            eine Table, welche in der ersten Spalte den Dienstnamen, in der Zweiten die ServiceURL und in der dritten Column den Port benötigt.
	 */
	public void unregisterService(Table inputTable) {

		String serviceName = inputTable.getRows().get(0).getValues().get(0).getStringValue();

		try {
			// Für die Delete-Prozedur muss der KeyLong rausgefunden werden.
			Value keyLong = findViewEntry(null, inputTable.getRows().get(0).getValues().get(0), null, inputTable.getRows().get(0).getValues().get(1),
					inputTable.getRows().get(0).getValues().get(2))//
							.getRows().get(0).getValues().get(0);

			Table unregisterSerivceTable = new Table();
			unregisterSerivceTable.setName("xpcasDeleteCASService");
			unregisterSerivceTable.addColumn(new Column("KeyLong", DataType.INTEGER));

			Row unregisterRow = new Row();
			unregisterRow.addValue(keyLong);

			unregisterSerivceTable.addRow(unregisterRow);

			// Hier wird der Eintrag aus der Datenbank-Tabelle gelöscht.
			spc.unsecurelyProcessProcedure(unregisterSerivceTable);
		} catch (Exception e) {
			logger.logError("The service " + serviceName + " could not be unregistered!", e);
			throw new RuntimeException(e);
		}

		// Wenn ein Service abgemeldet wird, müssen auch die Verweise aus den Maps und den Tabellen entfernt werden.
		Table unregisterNewsfeedListenerTable = new Table();
		unregisterNewsfeedListenerTable.addColumn(new Column("CASServiceName", DataType.STRING));
		unregisterNewsfeedListenerTable.addColumn(new Column("TableName", DataType.STRING));

		// Alle Tabellennamen aus der newsfeed-Map holen und danach die Einträge dazu in den Tabellen löschen.
		Row unregisterNewsfeedRow = new Row();

		if (newsfeeds.keySet().contains(serviceName)) {
			for (String entry : newsfeeds.get(serviceName)) {
				unregisterNewsfeedRow.addValue(new Value(serviceName, null));
				unregisterNewsfeedRow.addValue(new Value(entry, null));
			}
		}

		// Den Dienst erst aus den Tabellen und zum Schluss aus der lokalen Map löschen.
		unregisterNewsfeedListener(unregisterNewsfeedListenerTable);
	}

	/**
	 * Registriert auf welcher Tabelle eine Prozedur ein Update ausführt.
	 * 
	 * @param inputTable
	 *            Eine Table, welche den Prozedurnamen und den Tabellennamen enthält.
	 */
	public void registerProcedureNewsfeed(Table inputTable) {
		Table registerProcedureNewsfeedTable = new Table();
		registerProcedureNewsfeedTable.setName("xpcasInsertProcedureNewsfeed");
		registerProcedureNewsfeedTable.addColumn(new Column("KeyLong", DataType.INTEGER, OutputType.OUTPUT));
		registerProcedureNewsfeedTable.addColumn(new Column("KeyText", DataType.STRING));
		registerProcedureNewsfeedTable.addColumn(new Column("TableName", DataType.STRING));

		for (Row inputRow : inputTable.getRows()) {

			Row registerRow = new Row();
			registerRow.addValue(null);
			registerRow.addValue(inputRow.getValues().get(0));
			registerRow.addValue(inputRow.getValues().get(1));

			registerProcedureNewsfeedTable.addRow(registerRow);
		}
		try {
			spc.unsecurelyProcessProcedure(registerProcedureNewsfeedTable);
		} catch (Exception e) {
			logger.logError("Error while trying to register procedures: ", e);
			throw new RuntimeException(e);
		}

		// Nach erfolgreichem Eintragen in der Datenbank werden die Einträge der Map hinzugefügt.
		for (Row row : inputTable.getRows()) {
			registerServicenotifier(row.getValues().get(0).getStringValue(), row.getValues().get(1).getStringValue());
		}
	}

	/**
	 * Löscht einen Eintrag aus der xtcasProcedureNewsfeed anhand der übergebenen Parameter. Dazu werden Prozedurname und Tabellenname benötigt.
	 * 
	 * @param inputTable
	 *            Eine Table mit dem Prozedurnamen und dem Tabellennamen als String-Value in einer Row.
	 */
	public void unregisterProcedureNewsfeed(Table inputTable) {

		Table unregisterProcedureNewsfeedTable = new Table();
		unregisterProcedureNewsfeedTable.setName("xpcasDeleteProcedureNewsfeed");
		unregisterProcedureNewsfeedTable.addColumn(new Column("KeyLong", DataType.INTEGER));

		try {
			for (Row inputRow : inputTable.getRows()) {

				// Für die Delete-Prozedur muss der KeyLong rausgefunden werden.
				Table keyTable = findProcedureEntry(inputRow.getValues().get(0), inputRow.getValues().get(1));

				if (keyTable.getRows().size() <= 0) {
					throw new RuntimeException("The combination of procedurename and tablename could not be found! "
							+ inputRow.getValues().get(0).getStringValue() + " " + inputRow.getValues().get(1).getStringValue());
				}
				Value keyLong = keyTable.getRows().get(0).getValues().get(0);

				Row unregisterRow = new Row();
				unregisterRow.addValue(keyLong);

				unregisterProcedureNewsfeedTable.addRow(unregisterRow);
			}
			// Hier wird der Eintrag aus der Datenbank-Tabelle gelöscht.
			spc.unsecurelyProcessProcedure(unregisterProcedureNewsfeedTable);
		} catch (Exception e) {
			logger.logError("Error while trying to unregister procedures: ", e);
			throw new RuntimeException(e);
		}

		// Nach erfolgreichem Löschen der Einträge in der Datenbank werden diese auch in der Map gelöscht.
		for (Row row : inputTable.getRows()) {
			unregisterServicenotifier(row.getValues().get(0).getStringValue(), row.getValues().get(1).getStringValue());
		}
	}

	public void unregisterProcedureNewsfeedCompletely(Table inputTable) {

		Table unregisterProcedureNewsfeedTable = new Table();
		unregisterProcedureNewsfeedTable.setName("xpcasDeleteProcedureNewsfeed");
		unregisterProcedureNewsfeedTable.addColumn(new Column("KeyLong", DataType.INTEGER));

		try {
			for (Row inputRow : inputTable.getRows()) {
				// Für die Delete-Prozedur muss der KeyLong rausgefunden werden.
				Table tableWithKeyLongs = findProcedureEntry(inputRow.getValues().get(0), null);
				for (Row keyRow : tableWithKeyLongs.getRows()) {

					Row unregisterRow = new Row();
					unregisterRow.addValue(keyRow.getValues().get(0));

					unregisterProcedureNewsfeedTable.addRow(unregisterRow);
				}
			}
			// Hier wird der Eintrag aus der Datenbank-Tabelle gelöscht.
			spc.unsecurelyProcessProcedure(unregisterProcedureNewsfeedTable);
		} catch (Exception e) {
			logger.logError("Error while trying to unregister procedures: ", e);
			throw new RuntimeException(e);
		}

		// Nach erfolgreichem Löschen der Einträge in der Datenbank wird der Key auch in der Map gelöscht.
		for (Row row : inputTable.getRows()) {
			servicenotifier.remove(row.getValues().get(0).getStringValue());
		}
	}

	/**
	 * Registriert für welche Tabelle ein Dienst angetriggert werden möchte, wenn ein Update auf dieser durchgeführt wird.
	 * 
	 * @param inputTable
	 *            Eine Table mit den beiden Values CASServiceKey als int und dem Tabellennamen als String.
	 */
	public void registerNewsfeedListener(Table inputTable) {

		// Key zum CASServiceName herausfinden.
		Value serviceKey = findViewEntry(inputTable.getRows().get(0).getValues().get(0), null, null, null, null)//
				.getRows().get(0).getValues().get(0);

		Table registerNewsfeedTable = new Table();
		registerNewsfeedTable.setName("xpcasInsertNewsfeedListener");
		registerNewsfeedTable.addColumn(new Column("KeyLong", DataType.INTEGER, OutputType.OUTPUT));
		registerNewsfeedTable.addColumn(new Column("CASServiceKey", DataType.INTEGER));
		registerNewsfeedTable.addColumn(new Column("TableName", DataType.STRING));

		for (Row inputRow : inputTable.getRows()) {
			Row registerRow = new Row();
			registerRow.addValue(null);
			registerRow.addValue(serviceKey);
			registerRow.addValue(inputRow.getValues().get(1));

			registerNewsfeedTable.addRow(registerRow);
		}
		try {
			spc.unsecurelyProcessProcedure(registerNewsfeedTable);
		} catch (Exception e) {
			logger.logError("Error while trying to register a newsfeed: ", e);
			throw new RuntimeException(e);
		}

		// Nach erfolgreichem Eintragen in der Datenbank werden die Einträge der Map hinzugefügt.
		for (Row row : inputTable.getRows()) {
			registerNewsfeed(row.getValues().get(0).getStringValue(), row.getValues().get(1).getStringValue());
		}
	}

	/**
	 * Löscht einen Eintrag aus der xtcasNewsfeedListener anhand der übergebenen Parameter. Dazu werden CASServicename und Tabellenname benötigt.
	 * 
	 * @param inputTable
	 *            Eine Table mit dem CASServiceNamen und dem Tabellennamen als String-Value in einer Row.
	 */
	public void unregisterNewsfeedListener(Table inputTable) {

		Table unregisterNewsfeedTable = new Table();
		unregisterNewsfeedTable.setName("xpcasDeleteNewsfeedListener");
		unregisterNewsfeedTable.addColumn(new Column("KeyLong", DataType.INTEGER));

		try {
			for (Row inputRow : inputTable.getRows()) {

				// Für die Delete-Prozedur muss der KeyLong rausgefunden werden.
				Value keyLong = findViewEntry(inputRow.getValues().get(0), null, inputRow.getValues().get(1), null, null)//
						.getRows().get(0).getValues().get(0);

				Row unregisterRow = new Row();
				unregisterRow.addValue(keyLong);

				unregisterNewsfeedTable.addRow(unregisterRow);
			}
			// Hier wird der Eintrag aus der Datenbank-Tabelle gelöscht.
			spc.unsecurelyProcessProcedure(unregisterNewsfeedTable);
		} catch (Exception e) {
			logger.logError("Error while trying to unregister newsfeed: ", e);
			throw new RuntimeException(e);
		}
		// Nach erfolgreichem Löschen der Einträge in der Datenbank werden diese auch in der Map gelöscht.
		for (Row row : inputTable.getRows()) {
			unregisterNewsfeed(row.getValues().get(0).getStringValue(), row.getValues().get(1).getStringValue());
		}
	}

	/**
	 * Wenn das CAS neu gestartet wird, müssen die Servicenotifier wieder aus der Datenbank ausgelesen werden, da die Map sonst leer ist.
	 */
	@PostConstruct
	private void initializeServicenotifiers() {
		try {
			if (securityService.areServiceNotifiersStoresSetup()) {
				servicenotifier = new HashMap<>();
				Table servicenotifierTable = findViewEntry(null, null, null, null, null);
				for (Row row : servicenotifierTable.getRows()) {
					if (row.getValues().get(4) != null && row.getValues().get(5) != null) {
						registerServicenotifier(row.getValues().get(4).getStringValue(), row.getValues().get(5).getStringValue());
					}
				}
			}
		} catch (Exception e) {
			logger.logError("Error while trying to initialize servicenotifiers!", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Wenn das CAS neu gestartet wird, muss die Newsfeeds-Map wieder aus der Datenbank ausgelesen werden, da die Map sonst leer ist.
	 */
	@PostConstruct
	private void initializeNewsfeeds() {
		try {
			if (securityService.areServiceNotifiersStoresSetup()) {
				newsfeeds = new HashMap<>();
				Table newsfeedsTable = findViewEntry(null, null, null, null, null);
				for (Row row : newsfeedsTable.getRows()) {
					registerNewsfeed(row.getValues().get(3).getStringValue(), row.getValues().get(5).getStringValue());
				}
			}

		} catch (Exception e) {
			logger.logError("Error while trying to initialize newsfeed!", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Sucht in der xvCASServices die Einträge anhand der übergebenen Values heraus. Die Values müssen hierfür String-Values sein. Wichtig hierbei ist, dass
	 * nicht jeder Value übergeben werden muss. Falls man die gesamte View haben möchte, kann man auch einfach 'null' in allen Übergabeparametern übergeben.
	 * 
	 * @param casServiceName
	 *            Der Name des Dienstes als String-Value.
	 * @param procedureName
	 *            Der Name der Prozedur als String-Value.
	 * @param tableName
	 *            Der Name der Table als String-Value.
	 * @param serviceURL
	 *            Die URL eines Dienstes.
	 * @param port
	 *            Der Port eines Dienstes.
	 * @return Eine Table mit den jeweiligen gefilterten Einträgen. Die Reihenfolge der Values ist folgende: CASServiceKey, NewsfeedListenerKey,
	 *         ProcedureNewsfeedKey, CASServiceName, ProcedureName, TableName, ServiceURL, Port
	 */
	public Table findViewEntry(Value casServiceName, Value procedureName, Value tableName, Value serviceURL, Value port) {
		Table viewResult = new Table();

		Table viewTable = new Table();
		viewTable.setName("xvcasCASServices");
		viewTable.addColumn(new Column("CASServiceKey", DataType.INTEGER));
		viewTable.addColumn(new Column("NewsfeedListenerKey", DataType.STRING));
		viewTable.addColumn(new Column("ProcedureNewsfeedKey", DataType.STRING));
		viewTable.addColumn(new Column("CASServiceName", DataType.STRING));
		viewTable.addColumn(new Column("ProcedureName", DataType.STRING));
		viewTable.addColumn(new Column("TableName", DataType.STRING));
		viewTable.addColumn(new Column("ServiceURL", DataType.STRING));
		viewTable.addColumn(new Column("PORT", DataType.INTEGER));

		Row viewRow = new Row();
		viewRow.addValue(null);
		viewRow.addValue(null);
		viewRow.addValue(null);
		viewRow.addValue(casServiceName);
		viewRow.addValue(procedureName);
		viewRow.addValue(tableName);
		viewRow.addValue(serviceURL);
		viewRow.addValue(port);

		viewTable.addRow(viewRow);
		try {
			viewResult = securityService.unsecurelyGetIndexView(viewTable);
		} catch (Exception e) {
			logger.logError("Error while trying to access view xvcasCASServices!", e);
			throw new RuntimeException(e);
		}
		return viewResult;
	}

	/**
	 * Sucht in der xtcasProcedureNewsfeed die Einträge anhand der übergebenen Values heraus. Die Values müssen hierfür String-Values sein. Wichtig hierbei ist,
	 * dass nicht jeder Value übergeben werden muss. Falls man die gesamte Tabelle haben möchte, kann man auch einfach 'null' in allen Übergabeparametern
	 * übergeben. LastAction ist immer >=0.
	 * 
	 * @param procedureName
	 *            Der Name der Prozedur als String-Value.
	 * @param tableName
	 *            Der Name der Table als String-Value.
	 * @return Eine Table mit den gültigen KeyLongs zu der übergebenen Kombination.
	 */
	public Table findProcedureEntry(Value procedureName, Value tableName) {
		Table viewResult = new Table();

		Table viewTable = new Table();
		viewTable.setName("xtcasProcedureNewsfeed");
		viewTable.addColumn(new Column("KeyLong", DataType.INTEGER));
		viewTable.addColumn(new Column("KeyText", DataType.STRING));
		viewTable.addColumn(new Column("TableName", DataType.STRING));
		viewTable.addColumn(new Column("LastAction", DataType.INTEGER));

		Row viewRow = new Row();
		viewRow.addValue(null);
		viewRow.addValue(procedureName);
		viewRow.addValue(tableName);
		viewRow.addValue(new Value("0", ">="));

		viewTable.addRow(viewRow);
		try {
			viewResult = securityService.unsecurelyGetIndexView(viewTable);
		} catch (Exception e) {
			logger.logError("Error while trying to access view xtcasProcedureNewsfeed!", e);
			throw new RuntimeException(e);
		}
		return viewResult;
	}

	/**
	 * @return Eine Map der registrierten Dienste mit den Tabellen, auf welche sie horchen.
	 */
	public Map<String, List<String>> getServicenotifier() {
		return servicenotifier;
	}

	/**
	 * @return Eine Map der registrierten Prozeduren mit den Tabellen, welche sie verändern/updaten.
	 */
	public Map<String, List<String>> getNewsfeeds() {
		return newsfeeds;
	}

	/**
	 * Mappt, welche Prozeduren welche Tabellen verändern.
	 * 
	 * @param procedureName
	 *            Der Name der Prozedur.
	 * @param tableName
	 *            Der Name der Tabelle, welche durch die Prozedur verändert werden soll.
	 */
	public void registerServicenotifier(String procedureName, String tableName) {
		if (!servicenotifier.containsKey(procedureName)) {
			List<String> tables = new ArrayList<>();
			tables.add(tableName);
			servicenotifier.put(procedureName, tables);
		} else if (servicenotifier.containsKey(procedureName) && !servicenotifier.get(procedureName).contains(tableName)) {
			servicenotifier.get(procedureName).add(tableName);
		}
	}

	/**
	 * Entfernt den Eintrag einer Tabelle zu einer Prozedur aus der Map.
	 * 
	 * @param procedureName
	 *            Die Prozedur, zu welcher der Eintrag entfernt werden soll.
	 * @param tableName
	 *            Der Tabellenname, zu welchem die Prozedur entfernt werden soll.
	 */
	public void unregisterServicenotifier(String procedureName, String tableName) {
		if ((servicenotifier.containsKey(procedureName) && servicenotifier.get(procedureName).contains(tableName))) {
			servicenotifier.get(procedureName).remove(tableName);
		}
	}

	/**
	 * Mappt die Tabellen zu den Servicenamen und registriert somit, welche Services angetriggert werden müssen, wenn Änderungen auf bestimmten Tabellen
	 * stattfinden.
	 * 
	 * @param serviceName
	 *            Der Name des Dienstes
	 * @param tableName
	 *            Der Name der Tabelle, auf welche gehorcht werden soll
	 */
	public void registerNewsfeed(String serviceName, String tableName) {
		if (!newsfeeds.containsKey(serviceName)) {
			List<String> tables = new ArrayList<>();
			tables.add(tableName);
			newsfeeds.put(serviceName, tables);
		} else if (newsfeeds.containsKey(serviceName) && !newsfeeds.get(serviceName).contains(tableName)) {
			newsfeeds.get(serviceName).add(tableName);
		}
	}

	/**
	 * Entfernt den Eintrag einer Table zu einem Dienst aus der Map.
	 * 
	 * @param serviceName
	 *            Der Dienst, zu welchem der Eintrag entfernt werden soll.
	 * @param tableName
	 *            Der
	 */
	public void unregisterNewsfeed(String serviceName, String tableName) {
		if ((newsfeeds.containsKey(serviceName) && newsfeeds.get(serviceName).contains(tableName))) {
			newsfeeds.get(serviceName).remove(tableName);
		}
	}

//	/**
//	 * Prüft, ob minimalen die Datenbank-Objekte notwendig für das Registrieren von Diensten und Newsfeeds vorhanden sind. Dazu prüft man, ob die
//	 * `xvcasCASServices` vorhanden ist.
//	 * 
//	 * @return True, falls xvcascasservices vorhanden ist.
//	 * @throws Exception
//	 *             Fehler bei der Emittlung
//	 */
//	public boolean areServiceNotifiersStoresSetup() throws Exception {
//		return securityService.isTablePresent("xvcascasservices");
//	}

}
