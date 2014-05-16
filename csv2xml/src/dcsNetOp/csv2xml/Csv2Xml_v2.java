

import java.io.File;


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
    RemoveLeadingSpace
  }


  public StringBuffer tableToXml(File srcCsv, String prepad, String tableNodeName)
  {
        if (srcCsv.exists())
        {
                if (tableNodeName.isEmpty()) // if this is empty, use Source file name
                        tableNodeName = srcCsv.getName();
                StringBuffer toReturn = new StringBuffer();
                toReturn << prepad << "<table name='" <<  tableNodeName << "'>\n";

                InputFileStream  srcCsvStream = new InputFileStream (srcCSV);
                TokenizeCSVByLine(srcCsvStream.ReadLine, ",", RemoveLeadingSpace);

        }
  }

  

  public static void main(String[] args)
  {
  }
  
}
