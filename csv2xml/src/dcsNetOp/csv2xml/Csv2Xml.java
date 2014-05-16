package dcsNetOp.csv2xml;

import java.io.File;
import java.io.FileInputStream;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;

// file operations
import java.io.StringWriter;
import java.io.FileWriter;
import java.nio.file.*;


import java.util.Collections; // for array and collection functions

// In support of JSON functionality
import java.io.FileReader;
import java.io.IOException;
// JSON library
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
/*
 * Format for xml
 * 
 * <NetOp version=1.0>
 *   <meta>
 *     <start value=epochInSecond />
 *     <end value=epochInSecond />
 *     <checked time=epochInSecond />
 *   </meta>
 * 
 *   <table name="name or source file name">
 *     <bin value=second/>
 *     <header idx=1, name="" type=DataType/>
 *     <header idx=2, name="" type=DataType/>
 *     <header idx=End, name="" type=DataType/>
 *     <row>
 *       <vector idx=0 value=epochInSecond/> <!-- idx = 0 is always epoch in time and is required --!>
 *       <vector idx=integer value=Data/>
 *       ...
 *     </row>
 *   </table>
 *   <table>...
 *   
 * </NetOp>
 * 
 */

class Csv2Xml
{

  enum CsvEnum
  {
    None,
    TrimSpace
  }

  final static String PREPAD_CONST = "  ", DEFAULT_DELIMETER = ",";
  final static Long DEFAULT_BIN= new Long(60);

  public class HeaderType 
  {
    public String name, type;
    HeaderType(String name, String type)
    {
      this.name = name; this.type = type;
    }

    @Override
    public String toString()
    {
      return this.name + " : " +this.type + "\n";
    }
  }

  class TableMetaData
  {
    String srcFileName,nodeName, defaultDirectory, delimeter;
    Long bin, headerExist, ignoreHeader, walkTreeDepth;
    HashMap< Long, HeaderType > headers;
    TableMetaData()
    {
      this.headers = new HashMap<Long, HeaderType>();
    }

    @Override
    public String toString()
    {
      StringBuffer toReturn = new StringBuffer();
      toReturn.append(srcFileName).append("\n").append(nodeName).append("\n").append(defaultDirectory).append("\n").append(delimeter).append("\n").
        append(bin).append("\n").append(headerExist).append("\n").append(ignoreHeader).append("\n");
      for (Map.Entry< Long, HeaderType> entry : headers.entrySet())
        toReturn.append("key=").append(entry.getKey().toString()).append("\nvalues:\n").append(entry.getValue().toString()).append("\n");

      return toReturn.toString();
    }
  }

  static class ConvertConfig
  {
    TableMetaData[] metaTables;
    String version, sourceName;
    String prepad;
    String defaultFolder;
//    long startime, endtime, checkedtime; // epoch in second
    @Override
    public String toString()
    {
      StringBuffer toReturn = new StringBuffer();
      toReturn.append(prepad).append("\n").append(version).append("\n").append(sourceName).append("\n").append(defaultFolder).append("\n");
      for (int i =0 ; i < metaTables.length; i++)
        toReturn.append(metaTables[i].toString()).append("\n----\n");

      return toReturn.toString();
    }
  }


  public String[] tokenizeCSVByLine(String srcLine, String delimeter, CsvEnum option)
  {
    if (delimeter.isEmpty())
      return new String[] {srcLine};

    String[] result = srcLine.split(delimeter); // splits string by delimeter. Must be mon empty and delimeter at 1st position
    // clean up by removing leading space
    if (option == CsvEnum.TrimSpace)
    {
      for (int i = 0; i < result.length; i++)
        result[i] = result[i].trim();
    }

    return result;
  }


  public StringBuffer tableToXml(StringBuffer toReturn, TableMetaData tableMeta, String prepad) throws IOException
  {
    if ( (tableMeta == null) || (tableMeta.srcFileName == null) || (tableMeta.srcFileName.isEmpty()) )
      return null;

    Finder finder = new Finder(tableMeta.srcFileName);
    Path startingDirectory = Paths.get(tableMeta.defaultDirectory);
    Files.walkFileTree(startingDirectory, EnumSet.noneOf(FileVisitOption.class), tableMeta.walkTreeDepth.intValue() , finder);

    if (toReturn ==null)
      toReturn = new StringBuffer();

    for (Path file : finder.getMatched())
    {
      tableToXmlPerFind(toReturn, file, tableMeta, prepad);
    }

    return toReturn;
  }


  public StringBuffer tableToXmlPerFind(StringBuffer toReturn, Path srcFilePath, TableMetaData tableMeta, String prepad) throws IOException
  {
    if ( (tableMeta == null) || (tableMeta.srcFileName == null) || (tableMeta.srcFileName.isEmpty()) )
      return null;

    if (toReturn ==null)
      toReturn = new StringBuffer();

    File srcCsv = (srcFilePath == null)? new File (tableMeta.defaultDirectory + tableMeta.srcFileName) : new File (srcFilePath.toString());
    if (srcCsv.exists())
    {
      if (tableMeta.nodeName.isEmpty()) // if this is empty, use Source file name
      {
        tableMeta.nodeName = srcCsv.getName();
      }

      // table tag

      toReturn.append(prepad).append("<table name='").append(tableMeta.nodeName).append("'>\n");
      // table meta data
      toReturn.append(prepad).append(prepad).append("<bin value='").append( tableMeta.bin).append( "' />\n");
      boolean headerExistCheck=false;
      if ((tableMeta.headers == null)  || (tableMeta.headers.size() ==0) ) // header less
	// logg << headerless conversoin. Proceeding with position\n";
        // see if there is header specified  in src file
        //throw new IOException ("headerless conversoin."); // not allowed
       headerExistCheck=true;
      else // if there is header
      {
        if ((tableMeta.headerExist == 0 ) || (tableMeta.ignoreHeader == 1))
          for  (Map.Entry< Long, HeaderType> entry : tableMeta.headers.entrySet())
            toReturn.append( prepad).append( prepad).append("<header idx='").append(entry.getKey()
              .toString()).append("' name='").append(entry.getValue().name)
              .append( "' type='").append( entry.getValue().type ).append( "' />\n");
      }

      // compute max key for the headers
      int headermax = Collections.max(tableMeta.headers.keySet()).intValue(); // get maximum value of key which is integer type

      //try
      {
        // For Row section. Need to read source first.
        // read the source
        LineNumberReader srcCsvReader = new LineNumberReader (new FileReader(srcCsv));
        String srcLine;
        try 
        {

          while ( (srcLine = srcCsvReader.readLine()) != null ) // any empty line is discarded.
          {

            if (!srcLine.isEmpty())
            {
              String[] tokenized = tokenizeCSVByLine(srcLine, tableMeta.delimeter , CsvEnum.TrimSpace);

              if ((srcCsvReader.getLineNumber() == 1) // read 1 line
                  && (tableMeta.headerExist == 1)
                 )
              {
                if (tableMeta.ignoreHeader == 1)
                  continue; // skip to next line, ignore ing it.
                else
                {
                  if (!headerExistCheck)
                    for (int i =0; i < tokenized.length; i++)
                      toReturn.append(prepad).append( prepad).append("<header idx='").append(Integer.toString(i))
                        .append("' name='").append(tokenized[i]).append( "' type='' />\n");
                }
              }

              int hdiff = tokenized.length - headermax;
              if ( hdiff <0  )
              {
                //logg << "there are less number of elements in this row than vector header requires at"
               // << " file " << srcCSV.getName() << ", Line number(base 1): " <<  srcCsvReader.getLineNumber()
                // << ". Proceeding with empty space given to unavailble value for header posiiton"
              }
              else if (hdiff > 0)
              {
                //logg << "there are more number of elements in this row than vector header requires at"
               // << " file " << srcCSV.getName() << ", Line number(base 1): " <<  srcCsvReader.getLineNumber()
                // << " Proceeding with trunccations.
              }

              toReturn .append( prepad ).append( prepad).append( "<row>\n");
              for( Map.Entry<Long, HeaderType> entry : tableMeta.headers.entrySet())
              {
                if (tokenized.length <= entry.getKey()) // if key is bigger than length, than skip over
                  continue;
                toReturn.append( prepad).append(prepad).append( prepad).append( "<vector idx='").append( entry.getKey().toString() )
                  .append("' value='").append( tokenized[entry.getKey().intValue()]).append( "' />\n");
              }
              toReturn.append(prepad).append(prepad).append("</row>\n");
            }

          } // while 
        } finally
        {
          // close input
          srcCsvReader.close();
        } // finally
        // close table tag
        toReturn.append( prepad).append( "</table>\n");
      } // ned try
      /* catch (Exception ex)
      {
        System.err.println("Error in parsing a Source files"+srcCsv);
        ex.printStackTrace();
        toReturn =null; // clear off what ever happened;
      }
      */

    } // end if
    return toReturn;
  }
  
  public String netOpXml(ConvertConfig c2xmlConfig, String startTime, String endTime, String checkTime) throws IOException
  {
    if (c2xmlConfig == null)
      return null;

    StringBuffer toReturn = new StringBuffer();

    toReturn.append("<?xml version=\"1.0\"?>\n");
    toReturn.append( String.format("<NetOp version='%s' source='%s' >\n",c2xmlConfig.version,c2xmlConfig.sourceName));
    toReturn.append("  <meta>\n");
    toReturn.append(String.format("    <start value=%s />\n", startTime));
    toReturn.append(String.format("    <endt value=%s />\n",endTime));
    toReturn.append(String.format("    <checked value=%s />\n",checkTime));
    toReturn.append("  </meta>\n");


    for (TableMetaData csv: c2xmlConfig.metaTables) // iterate through
    {
      tableToXml(toReturn,csv, c2xmlConfig.prepad) ;
    }

    toReturn.append(  "</NetOp>\n");

    return toReturn.toString();

  }


  public String netOpXml(JSONObject c2xConfigRootPassed, String startTime, String endTime, String checkTime) throws Exception
  {
    if (c2xConfigRootPassed == null)
      return null;

    ConvertConfig c2xmlConfig = getConfigFromJson(c2xConfigRootPassed);
    return netOpXml(c2xmlConfig,startTime,endTime,checkTime);
  }

  public String netOpXml (String jsonConfigFile, String jsonCSV2XMLConfigRootName, String startTime, String endTime, String checkTime) throws Exception, IOException
  {

    ConvertConfig c2xmlConfig = getConfigFromJson(jsonConfigFile,jsonCSV2XMLConfigRootName);

    return netOpXml(c2xmlConfig,startTime,endTime,checkTime);
    
  }

  ConvertConfig getConfigFromJson(JSONObject c2xConfigRootPassed) throws Exception, IOException
  {
    if (c2xConfigRootPassed!=null)
    {
      ConvertConfig c2xConfig = new ConvertConfig();

      // get meta info
      c2xConfig.sourceName = (String) c2xConfigRootPassed.get("SourceName");
      c2xConfig.version = (String) c2xConfigRootPassed.get("Version");
      c2xConfig.defaultFolder = (String) c2xConfigRootPassed.get("DefaultFolder");
      if ( (c2xConfig.sourceName==null) || (c2xConfig.version ==null) || (c2xConfig.defaultFolder ==null) )
        throw new Exception ("Converion meta data SourceName, Version, and/or DefaultFolder are/is not supplied. This will cause confusion on CSAAC server!");
      c2xConfig.prepad = (String) c2xConfigRootPassed.get("Prepad");
      if (c2xConfig.prepad == null)
        c2xConfig.prepad = PREPAD_CONST;

      File defaultFolder = new File(c2xConfig.defaultFolder);
      if ( !defaultFolder.exists() )
        throw new IOException (String.format("Default Folder specified for Nagios RRD files (%s) does not exist",defaultFolder)); // get out

      JSONArray c2xConfigRoot = (JSONArray)c2xConfigRootPassed.get("ReportInfo");
      if (c2xConfigRoot == null)
        throw new Exception ("Conversion JSON configure file does not list Report CSV file to map to XML");

      System.err.printf("Total length of Configs: %d\n",c2xConfigRoot.size());
      c2xConfig.metaTables = new TableMetaData[c2xConfigRoot.size()]; // create same length
      for (int i=0; i < c2xConfig.metaTables.length; i++)
      {
        if (c2xConfig.metaTables[i] == null)
          c2xConfig.metaTables[i] = new TableMetaData();

        JSONObject srcConfObj = (JSONObject) c2xConfigRoot.get(i);
        if (srcConfObj!=null)
        {
          // srcFileName is array just in case I need to put more very easily. It could be object
          // Right now item 0 is always regexpression to find a file or collection of file for same metrics.
          JSONArray srcFileName = (JSONArray)srcConfObj.get("SrcFileName");
          if ((srcFileName == null) || (srcFileName.size() < 1) )
            throw new IOException ("SrcFileName fields is empty or mismatch in counts");
          c2xConfig.metaTables[i].srcFileName = (String)srcFileName.get(0); // get the file name pattern. Always assume RegEx.
          if (srcFileName.size()>1)
            c2xConfig.metaTables[i].walkTreeDepth = (Long)srcFileName.get(1); // get maximum depth. This is optional.
          else
            c2xConfig.metaTables[i].walkTreeDepth = new Long(Integer.MAX_VALUE); // search all sub directory.

          c2xConfig.metaTables[i].nodeName = (String)srcConfObj.get("NodeName");
          c2xConfig.metaTables[i].bin = (Long)srcConfObj.get("Bin");
          if (c2xConfig.metaTables[i].bin == null)
            c2xConfig.metaTables[i].bin = DEFAULT_BIN;
          c2xConfig.metaTables[i].defaultDirectory = (String) srcConfObj.get("DefaultFolder"); // location overrider
          if (c2xConfig.metaTables[i].defaultDirectory == null)
            c2xConfig.metaTables[i].defaultDirectory = c2xConfig.defaultFolder;
          c2xConfig.metaTables[i].delimeter = (String)srcConfObj.get("Delimeter");
          if (c2xConfig.metaTables[i].delimeter == null)
            c2xConfig.metaTables[i].delimeter = DEFAULT_DELIMETER;

          // processing headers
          JSONObject headers = (JSONObject)srcConfObj.get("Headers");
          if (headers != null)
          {
            c2xConfig.metaTables[i].headerExist = (Long)headers.get("exist");
            c2xConfig.metaTables[i].ignoreHeader = (Long)headers.get("ignore");
            JSONArray headerArray = (JSONArray)headers.get("HeaderInfo");
            if (headerArray == null)
              throw new IOException ("Configuration Json File does not have a HeaderInfo arrays");

            c2xConfig.metaTables[i].headers = new HashMap<Long, HeaderType >();
            for (int j = 0; j < headerArray.size(); j++)
            {
              JSONArray headerInfo = (JSONArray)headerArray.get(j);
              if (headerInfo!=null)
              {
                c2xConfig.metaTables[i].headers.put( (Long)headerInfo.get(0), new HeaderType( (String)headerInfo.get(1), (String)headerInfo.get(2)) );
              }
              else
                throw new Exception ("Configuration Json FIle does not have pair of value for header section");
            }
          }
          else
            throw new Exception ("Configuration Json file does not have header secion");
        }
        else
          throw new Exception ("Configuration Json file does not have expected array of TableMeta information");
      }

      return  c2xConfig;
    }
    else
      return null;
  }

  // warpper class if I need to read directly from file
  public ConvertConfig getConfigFromJson(String jsonFileName, String rootName) throws Exception, ParseException
  {
    
    File jsonf = new File (jsonFileName);
    JSONParser configParser = new JSONParser();
    try
    {
      JSONObject configRoot= (JSONObject) configParser.parse(new FileReader(jsonFileName));
      if ( (rootName != null) && !(rootName.isEmpty()) )
      {
        configRoot = (JSONObject) configRoot.get(rootName);
      }

      return getConfigFromJson(configRoot);
    }
    catch (IOException ioe)
    {
      ioe.printStackTrace();
    }
    return null;
  }

  public static void main(String[] args)
  {
    if (args.length < 2) 
    {
      System.out.println("java Csv2Xml jsonFile dest_xmlFile");
      System.exit(1);
    }

    Csv2Xml test = new Csv2Xml();

    try
    {
      ConvertConfig tt = test.getConfigFromJson(args[0],null);
      System.out.println(tt);

      //Calendar checked = Calendar.getInstance();  //right now
      long endTime = System.currentTimeMillis()/1000; // convert to seconds
      long startTime =endTime - 60;// move 60 seconds back
      FileWriter fwOut = new FileWriter(args[1]);
      fwOut.write(test.netOpXml(tt,Long.toString(startTime), Long.toString(endTime), Long.toString(System.currentTimeMillis()/1000) ) );
      fwOut.flush();
      fwOut.close();

    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
  
}
