package one.chartsy.charting.financial;

import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.AbstractDataSource;
import one.chartsy.charting.data.DataSetProperty;
import one.chartsy.charting.util.DoubleArray;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.zip.DataFormatException;

import javax.swing.SwingUtilities;

public class StockDataSource extends AbstractDataSource {
  public static final String DEFAULT_SYMBOL = "XYZ";

  private static final int HEADER_COUNT = 40;

  /**
   * Constant value for daily data.
   */
  public static final int DAILY = 0;

  /**
   * Constant value for weekly data.
   */
  public static final int WEEKLY = 1;

  /**
   * Constant value for monthly data.
   */
  public static final int MONTHLY = 2;

  // --------------------------------------------------------------------------
  private StockData stockData = null;
  private URL defaultURL = null;
  private Thread loader = null;
  private int frequency = DAILY;
  private String apiKey = "";

  /**
   * Intializes a new <code>StockDataSource</code>.
   */
  public StockDataSource() {
    this(null);
  }

  /**
   * Intializes a new <code>StockDataSource</code>.
   * 
   * @param defaultDataURL
   *          An URL to a default data file.
   */
  public StockDataSource(URL defaultDataURL) {
    this.defaultURL = defaultDataURL;
  }

  /**
   * Sets the API key.
   * 
   * @param apiKey String API key from barchart.com
   */
  public void setAPIKey(String apiKey) {
        this.apiKey = apiKey;  
  }
  
  /**
   * Gets the API key.
   */
  public String getAPIKey() {
        return apiKey;  
  }

  /**
   * Returns the symbol with which the stock data is associated.
   */
  public final String getSymbol() {
    return (stockData != null) ? stockData.symbol : null;
  }

  /**
   * Returns the dates associated with the stock data.
   */
  public final Date[] getDates() {
    if (stockData == null)
      return null;
    int count = stockData.dates.length - stockData.headerLen;
    Date[] res = new Date[count];
    System.arraycopy(stockData.dates, stockData.headerLen, res, 0, count);
    return res;
  }

  /**
   * Returns the date associated with the specified point.
   */
  public final Date getDate(int dataIdx) {
    if (stockData == null)
      return null;
    return stockData.dates[dataIdx + stockData.headerLen];
  }

  /**
   * Returns the frequency type of the data provided by this data source.
   * 
   * @see #loadData
   * @see #DAILY
   * @see #WEEKLY
   * @see #MONTHLY
   */
  public final int getFrequency() {
    return frequency;
  }

  /**
   * Resets the data source.
   */
  public void reset() {
    setStockData(null);
  }

  /**
   * Loads the close prices of the specified symbols
   * <p>
   * Loading is performed in a separate thread. Once data has been loaded, the
   * {@link LoadHook#dataLoaded(DoubleArray[])} method of the specified hook
   * is called in the event dispatch thread.
   * 
   * @parm symbols The considered symbols. array must be sorted into ascending
   *       order.
   */
  public DoubleArray[] loadData(String[] symbols, LoadHook afterLoad) {
    if (loader != null) {
      // Interrupt loading currently in process.
      // System.out.println("Interrupt [Secondary]");
      loader.interrupt();
    }
    if (afterLoad != null) {
      loader = new Loader(symbols, afterLoad);
      loader.start();
      return null;
    } else {
      return doLoad(symbols);
    }
  }

  /**
   * Loads the data for a given symbol.
   * <p>
   * Loading is performed in a separate thread. Once data has been loaded, the
   * {@link LoadHook#dataLoaded(StockDataSource)} method of the specified hook
   * is called in the event dispatch thread.
   * 
   * @param symbol
   *          The considered symbol.
   * @param startDate
   *          The start date of the query.
   * @param endDate
   *          The end date of the query.
   * @param frequency
   *          The frequency of the data.
   * @param afterLoad
   *          The load hook. The {@link #}
   */
  public void loadData(String symbol, Date startDate, Date endDate, int frequency, LoadHook afterLoad) {
    if (loader != null) {
      // Interrupt loading currently in process.
      // System.out.println("Interrupt");
      loader.interrupt();
    }
    this.frequency = frequency;
    if (afterLoad != null) {
      loader = new Loader(symbol, startDate, endDate, afterLoad);
      loader.start();
    } else
      setStockData(doLoad(symbol, startDate, endDate));
  }

  public void loadDefaultData() {
    loadDefaultData(defaultURL);
  }

  public synchronized void loadDefaultData(URL dataURL) {
    if (dataURL == null)
      return;
    this.frequency = DAILY;
    InputStream in = null;
    StockData stockData = null;
    try {
      int headerLen = (int) (HEADER_COUNT * 1.5);
      StockReader reader = new StockReader(true, headerLen);
      in = new BufferedInputStream(dataURL.openStream());
      stockData = reader.readData(DEFAULT_SYMBOL, true, in);
      stockData.headerLen = headerLen;
    } catch (Exception x2) {
      System.err.println("Could not load default data (" + x2.getMessage() + ")");
      x2.printStackTrace();
      stockData = null;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (Exception x3) {
          x3.printStackTrace();
        }
      }
    }
    setStockData(stockData);
  }

  /**
   * Loads the close data for the specified symbols.
   * 
   * @param symbols
   * @return An array of <code>DoubleArray</code> objects holding the close
   *         prices for the symbols. If the symbol does not exist or if the data
   *         could not be fetched, a <code>null</code> element is inserted in
   *         the returned array.
   */
  private synchronized DoubleArray[] doLoad(String[] symbols) {
    Date[] dates = getDates();
    if (dates == null)
      return null;
    Date startDate = dates[0];
    Date endDate = dates[dates.length - 1];
    StockReader reader = new StockReader(true, 0);
    DoubleArray[] res = new DoubleArray[symbols.length];
    StockData data = null;
    for (int i = 0; i < symbols.length; ++i) {
      String symbol = symbols[i];
      try {
        data = reader.readData(symbol, startDate, endDate, frequency, apiKey);
        if (reader.interrupted)
          return null;
        res[i] = new DoubleArray();
        double[] vals = data.data[3];
        int cmp;
        for (int j = 0, k = 0; j < data.dates.length && k < dates.length;) {
          cmp = data.dates[j].compareTo(dates[k]);
          if (cmp < 0) {
            ++j; // Skip value
          } else if (cmp > 0) {
            ++k;
            if (j > 0)
              res[i].add(vals[j - 1]); // Keep previous value
            else
              res[i].add(0);
          } else {
            res[i].add(vals[j]);
            ++k;
            ++j;
          }
        }
        // System.out.println("Dates: " + dates.length + " --> " +
        // res[i].size());
      } catch (Exception x) {
        System.err.println("Could not load data for symbol: " + symbol + " (" + x.getMessage() + ")");
        // x.printStackTrace();
        res[i] = null;
      }
    }
    return res;
  }

  /**
   * Loads the data for a given symbol.
   * 
   * @param symbol
   *          The considered symbol.
   * @param startDate
   *          The start date of the query.
   * @param endDate
   *          The end date of the query.
   */
  private synchronized StockData doLoad(String symbol, Date startDate, Date endDate) {
    int headerDayCount = 0;
    if (frequency == MONTHLY)
      headerDayCount = HEADER_COUNT * 31;
    else if (frequency == WEEKLY)
      headerDayCount = HEADER_COUNT * 7;
    else
      headerDayCount = (int) (HEADER_COUNT * 1.5);

    StockReader reader = new StockReader(true, headerDayCount);
    StockData stockData = null;
    try {
      stockData = reader.readData(symbol, startDate, endDate, frequency, apiKey);
      if (reader.interrupted)
        return null;
      stockData.computeHeaderLength(startDate);
    } catch (Exception x) {
      System.err.println("Could not load data for symbol: " + symbol + " (" + x.getMessage() + ")");
      x.printStackTrace();
      if (defaultURL != null) {
        this.frequency = DAILY;
        System.err.println("Loading default data.");
        InputStream in = null;
        try {
          in = new BufferedInputStream(defaultURL.openStream());
          stockData = reader.readData(DEFAULT_SYMBOL, true, in);
          if (reader.interrupted)
            return null;
          stockData.headerLen = (int) (HEADER_COUNT * 1.5);
        } catch (Exception x2) {
          System.err.println("Could not load default data (" + x2.getMessage() + ")");
          x2.printStackTrace();
          stockData = null;
        } finally {
          if (in != null) {
            try {
              in.close();
            } catch (Exception x3) {
              x3.printStackTrace();
            }
          }
        }
      }
    }
    return stockData;
  }

  /** Sets the stock data. */
  private void setStockData(StockData data) {
    stockData = data;
    if (stockData == null) {
      getDataSetList().setDataSets(null);
    } else {
      DataSet[] dataSets = new DataSet[stockData.dataDesc.length];
      for (int i = 0; i < dataSets.length; ++i) {
        dataSets[i] = new DataSet(stockData.dataDesc[i], stockData.data[i]);
      }
      getDataSetList().setDataSets(dataSets);
    }
  }

  // --------------------------------------------------------------------------
  public interface LoadHook {
    /**
     * Invoked when the loading of data through the {@link #loadData} method is
     * completed.
     */
    void dataLoaded(StockDataSource stockDS);

    /**
     * Invoked when the loading of data through the {@link #loadData} method is
     * completed.
     */
    void dataLoaded(DoubleArray[] data);

  }

  private class Loader extends Thread {
    String symbol;
    Date startDate, endDate;
    LoadHook afterLoad;
    String[] symbols;

    Loader(String[] symbols, LoadHook afterLoad) {
      this.symbols = symbols;
      this.afterLoad = afterLoad;
    }

    Loader(String symbol, Date startDate, Date endDate, LoadHook afterLoad) {
      this.symbol = symbol;
      this.startDate = startDate;
      this.endDate = endDate;
      this.afterLoad = afterLoad;
    }

    @Override
    public void run() {
      if (symbol != null) {
        // System.out.println("Primary Loading: " + this + "[ "
        // +StockDataSource.this.loader);
        // Load the quotes for the specified symbol in the data source
        final StockData data = doLoad(symbol, startDate, endDate);
        StockDataSource.this.loader = null;
        if (afterLoad != null) { // threaded
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              setStockData(data);
              afterLoad.dataLoaded(StockDataSource.this);
            }
          });
        }
      } else {
        // System.out.println("Secondary Loading: " + this + "[ "
        // +StockDataSource.this.loader);
        // Load close data for secondary symbols
        final DoubleArray[] data = doLoad(symbols);
        StockDataSource.this.loader = null;
        if (afterLoad != null) { // threaded
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              afterLoad.dataLoaded(data);
            }
          });
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // Accessors to data sets
  public one.chartsy.charting.data.DataSet getOpenDataSet() {
    return isLoaded() ? get(0) : null;
  }

  public one.chartsy.charting.data.DataSet getHighDataSet() {
    return isLoaded() ? get(1) : null;
  }

  public one.chartsy.charting.data.DataSet getLowDataSet() {
    return isLoaded() ? get(2) : null;
  }

  public one.chartsy.charting.data.DataSet getCloseDataSet() {
    return isLoaded() ? get(3) : null;
  }

  public one.chartsy.charting.data.DataSet getVolumeDataSet() {
    return isLoaded() ? get(4) : null;
  }

  private boolean isLoaded() {
    return size() >= 5;
  }

  // --------------------------------------------------------------------------
  /**
   * Holds the result of a stock query.
   */
  private static class StockData {
    String symbol;
    String[] dataDesc;
    Date[] dates;
    double[][] data;
    int headerLen;

    StockData(String symbol, String[] dataDesc, Date[] dates, double[][] data) {
      this.symbol = symbol;
      this.dataDesc = dataDesc;
      this.dates = dates;
      this.data = data;
    }

    private void computeHeaderLength(Date startDate) {
      if (dates == null) {
        headerLen = 0;
      } else
        for (int i = 0; i < dates.length; ++i) {
          if (dates[i].compareTo(startDate) >= 0) {
            headerLen = i;
            return;
          }
        }
    }
  }

  // --------------------------------------------------------------------------
  /**
   * A data set class that holds the data loaded from a stock query.
   */
  public class DataSet extends AbstractDataSet {
    private final double[] data;

    DataSet(String name, double[] data) {
      this.data = data;
      DataSetProperty.setCategory(this, Double.valueOf(1));
      setName(name);
    }

    @Override
    public int size() {
      return data.length - stockData.headerLen;
    }

    @Override
    public boolean isXValuesSorted() {
      return true;
    }

    @Override
    public double getXData(int idx) {
      return idx;
    }

    @Override
    public double getYData(int idx) {
      return data[idx + stockData.headerLen];
    }

    public final int getHeaderLength() {
      return stockData.headerLen;
    }

    public final double[] getAllData() {
      return data.clone();
    }
  }

  // --------------------------------------------------------------------------
  private static class StockReader {
        private static final String BARCHART_URL = "https://marketdata.websol.barchart.com/getHistory.csv?";
        private final boolean reverse;
    //private int headerLength;
    private boolean interrupted;

    /**
     * Initializes a new reader, specifying whether the read values should be
     * reversed.
     * 
     * @param reverse
     *          If <code>true</code>, the read values will be reversed.
     * @param headerLength
     *          The number of header data.
     */
    public StockReader(boolean reverse, int headerLength) {
      this.reverse = reverse;
      //this.headerLength = headerLength;
    }

    // private StreamTokenizer createTokenizer(Reader reader) {
    // StreamTokenizer st = new StreamTokenizer(reader);
    // st.quoteChar(QUOTE_CHAR);
    // st.wordChars(' ', ' ');
    // st.ordinaryChar('-');
    // st.eolIsSignificant(true);
    // st.whitespaceChars(',', ',');
    // return st;
    // }

    /**
     * Loads data from the given <code>InputStream</code>.
     * 
     * @return An array containing the data sets.
     * @throws ParseException
     *           Parsing a date failed.
     * @throws NumberFormatException
     *           Parsing a date failed.
     * @throws DataFormatException
     *           The file is not a valid Yahoo CSV format; this exception is
     *           thrown when a line is being parsed and something other than a
     *           number has been read.
     * @throws IOException
     *           Opening (in the case of a URL) or reading the input stream
     *           failed.
     */
    private StockData readData(String symbol, boolean isDefaultSymbol, InputStream in)
        throws ParseException, NumberFormatException, DataFormatException, IOException {
      // == StreamTokenizer is not used because it is slower (this is
      // == mainly due to the fact that dates are not quoted and the parsing
      // == is thus more complicated.
      BufferedReader reader = makeReader(in);
      List<String> seriesNames = new ArrayList<String>();

      StringTokenizer tok = null;
      String line = reader.readLine();
      if (line.startsWith("No data"))
        throw new DataFormatException("No Data");
      tok = new StringTokenizer(line, ",");
      while (tok.hasMoreTokens())
        seriesNames.add(tok.nextToken());

      int seriesCount = seriesNames.size() - (isDefaultSymbol ? 1 : 4);

      if (seriesCount <= 0)
        throw new DataFormatException("Data Header expected");

      // Holds the data
      DoubleArray[] values = new DoubleArray[seriesCount];
      for (int i = 0; i < seriesCount; ++i) {
        values[i] = new DoubleArray();
      }

      // Holds the dates
      List<Date> dateList = new ArrayList<Date>();
      String pattern = isDefaultSymbol ? "yyyy-MM-dd" : "\"yyyy-MM-dd\"";
      DateFormat dateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);
      String str;
      line = reader.readLine();
      if (line == null)
        throw new IOException("No result");

      int i = (isDefaultSymbol ? -1 : -3);
      while (line != null && !interrupted) {
        tok = new StringTokenizer(line, ",");
        int countTokens = tok.countTokens();
        if (countTokens > 1) {
          while (tok.hasMoreTokens()) {
            str = tok.nextToken();
            if (i == -1)
              dateList.add(dateFormat.parse(str));
            else if (i >= 0 && i < seriesCount) {
              if (!isDefaultSymbol)
                str = str.replaceAll("\"", "");
              values[i].add(Double.parseDouble(str));
            }
            ++i;
          }
          if (i != seriesCount + (isDefaultSymbol ? 0 : 1))
            throw new DataFormatException("Expected number");
        }
        line = reader.readLine();
        i = (isDefaultSymbol ? -1 : -3);
        interrupted = Thread.currentThread().isInterrupted();
      }
      if (interrupted)
        return null;
      Date[] dates = new Date[dateList.size()];
      dateList.toArray(dates);
      if (this.reverse)
        reverseArray(dates, dates.length);

      String[] desc = new String[seriesCount];
      double[][] data = new double[seriesCount][];

      for (i = 0; i < seriesCount; ++i) {
        if (this.reverse)
          values[i].reverse();
        values[i].trim();
        data[i] = values[i].data();
        desc[i] = seriesNames.get(i + (isDefaultSymbol ? 1 : 3));
      }

      return new StockData(symbol, desc, dates, data);
    }

    /** Reads data from the given URL. */
    public StockData readData(String symbol, Date startDate, Date endDate, int frequency, String apiKey)
        throws Exception {
      String url = makeRequestURL(symbol, startDate, endDate, frequency, apiKey);
      InputStream in = null;
      StockData data = null;
      try {
        in = new BufferedInputStream(new URL(url).openStream());
        // long start = System.currentTimeMillis();
        data = readData(symbol, false, in);
        // System.out.println("Elapsed: " + (System.currentTimeMillis()-start));
      } catch (Exception x) {
        throw x;
      } finally {
        if (in != null)
          in.close();
      }
      return data;
    }

    /**
     * Returns the request URL to the Yahoo site.
     */
    private String makeRequestURL(String symbol, Date startDate, Date endDate, int frequency, String apiKey) {
      StringBuffer url = new StringBuffer(BARCHART_URL);

      // Symbol.
      url.append("symbol=" + symbol);
      
      // Start and End dates.
      DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
      url.append("&startDate=" + dateFormat.format(startDate));
      url.append("&endDate=" + dateFormat.format(endDate));
      
      // Type.
      switch (frequency) {
      case MONTHLY:
        url.append("&type=monthly");
        break;
      case WEEKLY:
        url.append("&type=weekly");
        break;
      case DAILY:
      default:
        url.append("&type=daily");
        break;
      }
      
      // Order.
      url.append("&order=desc");
      
      // API Key.
      url.append("&apikey=" + apiKey);
      
      return url.toString();
    }

    /**
     * Returns a <code>Reader</code> object on the specified
     * <code>InputStream</code>.
     */
    private BufferedReader makeReader(InputStream in) throws IOException {
      return new BufferedReader(new InputStreamReader(in));
    }
  }

  /**
   * Reverse the contents of the specified array.
   */
  private static void reverseArray(Object[] array, int count) {
    Object tmp;
    int start = 0, end = count - 1;
    while (start < end) {
      tmp = array[start];
      array[start] = array[end];
      array[end] = tmp;
      ++start;
      --end;
    }
  }
}

