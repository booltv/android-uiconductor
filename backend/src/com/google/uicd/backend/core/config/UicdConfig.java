// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.uicd.backend.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.uicd.backend.core.exceptions.UicdExcpetion;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Global config class for Uicd com.google.uicd.backend.core The variables such as DB connections
 * and test output home dir need to be set before use uicd com.google.uicd.backend.core
 */
public class UicdConfig {

  protected UicdConfig() {
    // Exists only to defeat instantiation.
  }

  private static final Logger logger = LogManager.getLogManager().getLogger("uicd");
  private static UicdConfig instance = new UicdConfig();

  private static final String USERNAME_KEYWORD = "username";
  private static final String UICDDATA_PATH_KEYWORD = "uicddatapath";
  private static final String MYSQLPORT_KEYWORD = "mysqlport";
  private static final String MYSQL_CONNECTION_STRING_KEYWORD = "mysqlconnectionstr";
  private static final String XML_DUMPER_APK_VERSION_KEYWORD = "xmldumperversion";
  private static final String ENABLE_IMAGE_VALIDATION_KEYWORD = "enableimagevalidation";
  private static final String CONFIG_SPLITTER = "=";
  private static final String XML_DUMPER_APK_FOLDER_NAME = "xmldumper_apks/";
  private static final String OUTPUT_FOLDER_NAME = "output/";
  private static final String INPUT_FOLDER_NAME = "input/";
  private static final String UICD_DEP_FOLDER_PATH = "deps";
  private static final String ADB_FORWARD_START_PORT = "adb_forward_start_port";
  private static final String LOG_OUTPUT_LEVEL = "log_output_level";
  private static final String ADB_PATH = "adb_path";

  private String adbShellPath = "adb";
  private Connection connection;
  private String currentUser = System.getProperty("user.name");
  private static final String UICD_DEFAULT_DATA_PATH =
      System.getProperty("user.dir") + "/uicd-datapath";

  // In MobileHarness, each thread will have different BasePath, however in spring, we set the
  // basePath in the main thread(child thread generated by spring automatically).
  private final HashMap<Long, String> uicdBasePathThreadMap = new HashMap<>();
  private int mysqlport = 3308;
  private String mysqlConnectionString = "";
  private int adbForwardStartPort = 6790;
  private Level logLevel = Level.INFO;
  private String xmlDumperApkVersion = "1.0.1";
  private boolean imageValidationOption = false;

  public static UicdConfig getInstance() {
    return instance;
  }

  /* Get the adb shell path. */
  public String getAdbShellPath() {
    return adbShellPath;
  }

  public void setAdbShellPath(String adbShellPath) {
    this.adbShellPath = adbShellPath;
  }

  public String getDBConnStr() {
    if (mysqlConnectionString != null && !mysqlConnectionString.isEmpty()) {
      return mysqlConnectionString;
    }
    return "jdbc:mysql://localhost:" + mysqlport
        + "/uicddb?autoReconnect=true&user=root&password=uicdawesome&useUnicode=true"
        + "&characterEncoding=utf-8";
  }

  public Connection getDBConnection() throws UicdExcpetion {
    if (connection == null) {
      this.initDefaultLocalDBConnection();
    }
    return connection;
  }


  public void setDBConnection(Connection connection) {
    this.connection = connection;
  }

  public void initDefaultLocalDBConnection() throws UicdExcpetion {
    try {
      connection =
          DriverManager.getConnection(getDBConnStr());
    } catch (SQLException e) {
      throw new UicdExcpetion("Cannot connect to Mysql");
    }
  }

  public synchronized String getBaseFolder() {
    if (uicdBasePathThreadMap.isEmpty()) {
      return UICD_DEFAULT_DATA_PATH;
    }
    if (uicdBasePathThreadMap.containsKey(Thread.currentThread().getId())) {
      return uicdBasePathThreadMap.get(Thread.currentThread().getId());
    }
    // In the Uicd local App mode, the UicdConfig object is set in the main thread. Each request
    // from the frontend is in a different thread. We should just use the basePath from the main
    // thread.
    return uicdBasePathThreadMap.values().stream().findFirst().get();
  }

  // used by uicd driver
  public synchronized void setBaseFolder(String uicdDataPath) {
    uicdBasePathThreadMap.put(Thread.currentThread().getId(), uicdDataPath);
  }

  /* Home folder for uicd output file */
  public String getTestOutputFolder() {
    return Paths.get(getBaseFolder(), OUTPUT_FOLDER_NAME).toString();
  }

  /* Home folder for uicd input file */
  public String getTestInputFolder() {
    return Paths.get(getBaseFolder(), INPUT_FOLDER_NAME).toString();
  }

  public Path getXmlDumperAPKPath() {
    return Paths.get(getBaseFolder(), UICD_DEP_FOLDER_PATH, XML_DUMPER_APK_FOLDER_NAME);
  }

  public String getDepsFolder() {
    return Paths.get(getBaseFolder(), UICD_DEP_FOLDER_PATH).toString();
  }

  public synchronized void parseConfigFile(Reader fileContents) throws IOException {
    HashMap<String, String> configVars = new HashMap<>();
    String line;
    BufferedReader bufferedReader = new BufferedReader(fileContents);
    while ((line = bufferedReader.readLine()) != null) {
      List<String> strs = Splitter.on(CONFIG_SPLITTER).splitToList(line);
      if (strs.size() >= 2) {
        configVars.put(strs.get(0).toLowerCase(),
            strs.stream().skip(1).collect(Collectors.joining(CONFIG_SPLITTER)));
      }
    }
    if (configVars.containsKey(USERNAME_KEYWORD)) {
      this.currentUser = configVars.get(USERNAME_KEYWORD);
    }
    if (configVars.containsKey(UICDDATA_PATH_KEYWORD)) {
      uicdBasePathThreadMap.put(
          Thread.currentThread().getId(), configVars.get(UICDDATA_PATH_KEYWORD));
    }
    if (configVars.containsKey(MYSQLPORT_KEYWORD)) {
      this.mysqlport = Integer.parseInt(configVars.get(MYSQLPORT_KEYWORD));
    }
    if (configVars.containsKey(MYSQL_CONNECTION_STRING_KEYWORD)) {
      this.mysqlConnectionString = configVars.get(MYSQL_CONNECTION_STRING_KEYWORD);
    }
    if (configVars.containsKey(LOG_OUTPUT_LEVEL)) {
      this.logLevel = Level.parse(configVars.get(LOG_OUTPUT_LEVEL));
    }
    if (configVars.containsKey(ADB_FORWARD_START_PORT)) {
      this.adbForwardStartPort = Integer.parseInt(configVars.get(ADB_FORWARD_START_PORT));
    }
    if (configVars.containsKey(XML_DUMPER_APK_VERSION_KEYWORD)) {
      this.xmlDumperApkVersion = configVars.get(XML_DUMPER_APK_VERSION_KEYWORD);
    }
    if (configVars.containsKey(ADB_PATH)) {
      this.adbShellPath = configVars.get(ADB_PATH);
    }
    if (configVars.containsKey(ENABLE_IMAGE_VALIDATION_KEYWORD)) {
      this.imageValidationOption =
          Boolean.parseBoolean(configVars.get(ENABLE_IMAGE_VALIDATION_KEYWORD));
    }
  }

  public void loadFromConfigFile(String cfgFilePath) throws UicdExcpetion {
    try {
      File file = new File(cfgFilePath);
      try (Reader fileReader = Files.newBufferedReader(file.toPath(), UTF_8)) {
        parseConfigFile(fileReader);
      }
    } catch (IOException e) {
      throw new UicdExcpetion("Cannot find uicd.cfg");
    }
  }

  public String getCurrentUser() {
    return currentUser;
  }

  public Level getLogLevel() {
    return logLevel;
  }

  public void setLogLevel(Level logLevel) {
    this.logLevel = logLevel;
  }

  public int getAdbForwardStartPort() {
    return adbForwardStartPort;
  }

  public String getXmlDumperApkVersion() {
    return xmlDumperApkVersion;
  }

  public boolean getImageValidationOption() {
    return imageValidationOption;
  }

  public void resetDBConnection() {
    try {
      connection.close();
    } catch (SQLException e) {
      logger.warning("Exception happened during connection close.");
    }
  }

  public static void reset() {
    UicdConfig.getInstance().resetDBConnection();
    instance = new UicdConfig();
  }
}
