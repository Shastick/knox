/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.gateway.shell.knox.token.Token;
import org.apache.hadoop.gateway.util.JsonUtils;

/**
 *
 */
public class KnoxSh {

  private static final String USAGE_PREFIX = "KnoxCLI {cmd} [options]";
  final static private String COMMANDS =
      "   [--help]\n" +
      "   [" + KnoxInit.USAGE + "]\n" +
      "   [" + KnoxDestroy.USAGE + "]\n" +
      "   [" + KnoxList.USAGE + "]\n";

  /** allows stdout to be captured if necessary */
  public PrintStream out = System.out;
  /** allows stderr to be captured if necessary */
  public PrintStream err = System.err;

  private Command command;
  private String gateway = null;
  /* (non-Javadoc)
   * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
   */
  public int run(String[] args) throws Exception {
    int exitCode = 0;
    try {
      exitCode = init(args);
      if (exitCode != 0) {
        return exitCode;
      }
      if (command != null && command.validate()) {
        command.execute();
      } else {
        out.println("ERROR: Invalid Command" + "\n" + "Unrecognized option:" +
            args[0] + "\n" +
            "A fatal exception has occurred. Program will exit.");
        exitCode = -2;
      }
    } catch (Exception e) {
      e.printStackTrace( err );
      err.flush();
      return -3;
    }
    return exitCode;
  }

  /**
   * Parse the command line arguments and initialize the data
   * <pre>
   * % knoxcli version
   * % knoxcli service-test [--u user] [--p password] [--cluster clustername] [--hostname name] [--port port]
   *
   * </pre>
   * @param args
   * @return
   * @throws IOException
   */
  private int init(String[] args) throws IOException {
    if (args.length == 0) {
      printKnoxShellUsage();
      return -1;
    }
    for (int i = 0; i < args.length; i++) { // parse command line
      if ( args[i].equals("destroy") ) {
        command = new KnoxDestroy();
      } else if ( args[i].equals("init") ) {
        command = new KnoxInit();
      } else if ( args[i].equals("list") ) {
        command = new KnoxList();
      } else if (args[i].equals("--gateway")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.gateway = args[++i];
      } else if (args[i].equals("--help")) {
        printKnoxShellUsage();
        return -1;
      } else {
        printKnoxShellUsage();
        //ToolRunner.printGenericCommandUsage(System.err);
        return -1;
      }
    }
    return 0;
  }

  private void printKnoxShellUsage() {
    out.println( USAGE_PREFIX + "\n" + COMMANDS );
    if ( command != null ) {
      out.println(command.getUsage());
    } else {
      char[] chars = new char[79];
      Arrays.fill( chars, '=' );
      String div = new String( chars );

      out.println( div );
      out.println( KnoxInit.USAGE + "\n\n" + KnoxInit.DESC );
      out.println();
      out.println( div );
      out.println(KnoxDestroy.USAGE + "\n\n" + KnoxDestroy.DESC);
      out.println();
      out.println( div );
      out.println(KnoxList.USAGE + "\n\n" + KnoxList.DESC);
      out.println();
      out.println( div );
    }
  }

  private abstract class Command {
    public boolean validate() {
      return true;
    }

    public abstract void execute() throws Exception;

    public abstract String getUsage();
  }

  private class KnoxInit extends Command {

    public static final String USAGE = "init";
    public static final String DESC = "Initializes a Knox token session.";

    @Override
    public void execute() throws Exception {
      Credentials credentials = new Credentials();
      credentials.add("ClearInput", "Enter username: ", "user")
                      .add("HiddenInput", "Enter pas" + "sword: ", "pass");
      credentials.collect();

      String username = credentials.get("user").string();
      String pass = credentials.get("pass").string();

      if (gateway == null) {
        gateway = System.getenv("GATEWAY_HOME");
      }

      Hadoop session = Hadoop.login(gateway, username, pass);

      String text = Token.get( session ).now().getString();
      Map<String, String> json = JsonUtils.getMapFromJsonString(text);

      //println "Access Token: " + json.access_token
      System.out.println("knoxinit successful!");
      displayTokenDetails(json);

      File tokenfile = new File(System.getProperty("user.home"), ".knoxtokencache");
      FileOutputStream fos = new FileOutputStream(tokenfile);
      fos.write(text.getBytes("UTF-8"));

      Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
      fos.close();

      //add owners permission only
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);

      Files.setPosixFilePermissions(Paths.get(System.getProperty("user.home") + "/.knoxtokencache"), perms);

      session.shutdown();
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class KnoxDestroy extends Command {

    public static final String USAGE = "version";
    public static final String DESC = "Displays Knox version information.";

    @Override
    public void execute() throws Exception {
      File tokenfile = new File(System.getProperty("user.home"), ".knoxtokencache");
      tokenfile.delete();
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class KnoxList extends Command {

    public static final String USAGE = "version";
    public static final String DESC = "Displays Knox version information.";

    @Override
    public void execute() throws Exception {
      String tokenfile = readFile(
          System.getProperty("user.home") +
          File.separator + ".knoxtokencache");

      if (tokenfile != null) {
        Map<String, String> json = JsonUtils.getMapFromJsonString(tokenfile);
        displayTokenDetails(json);
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private void displayTokenDetails(Map<String, String> json) {
    System.out.println("Token Type: " + json.get("token_type"));

    DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss");

    long milliSeconds= Long.parseLong(json.get("expires_in"));

    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliSeconds);
    System.out.println("Expires On: " + formatter.format(calendar.getTime()));
  }

  private String readFile(String file) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader (file));
    String line = null;
    String content = null;
    StringBuilder  stringBuilder = new StringBuilder();
    String ls = System.getProperty("line.separator");

    try {
        while((line = reader.readLine()) != null) {
            stringBuilder.append(line);
            stringBuilder.append(ls);
        }

        content = stringBuilder.toString();
    } finally {
        reader.close();
    }
    return content;
}

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    KnoxSh sh = new KnoxSh();
    int res = sh.run(args);
    System.exit(res);
  }
}