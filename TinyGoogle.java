import java.io.*;
import java.util.*;
import java.lang.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TinyGoogle {
    private static final Log LOG = LogFactory.getLog(TinyGoogle.class);

    public static HashMap<String, ArrayList<IndexPair>> invertedIndex = new HashMap<String, ArrayList<IndexPair>>();
    public static Path inPath;
    public static Path outPath;

    public static class IndexPair implements Comparable<IndexPair> {
        public String t;
        public int l;

        public IndexPair(String t, int l) {
            this.t = t;
            this.l = l;
        }

        public String getKey() {
            return t;
        }

        public int getValue() {
            return l;
        }

        @Override
        public int compareTo(IndexPair p) {
            if (p.getValue() > l) {
                return -1;
            } else if (p.getValue() < l) {
                return 1;
            } else {
                return 0;
            }
        }
    }
    public static class InvertedIndexMapper extends Mapper<LongWritable, Text, Text, Text> {

        private final static Text word = new Text();

        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            // get filename
            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            String fileName = fileSplit.getPath().getName();

            // get document ID
            String docId = fileName.replace(".txt","");

            // get each line of document
            String line = value.toString().replaceAll("\\p{Punct}", "").toLowerCase();

            // divide lines into tokens
            StringTokenizer tokenizer = new StringTokenizer(line);

            // map output is (word, docId)
            while (tokenizer.hasMoreTokens()) {
                String nextToken = tokenizer.nextToken();
                // don't count the docId word
                if (docId.equals(nextToken)) {
                    continue;
                }
                word.set(nextToken);
                context.write(word, new Text(docId));
            }
        }
    }

    public static class InvertedIndexReducer extends Reducer<Text, Text, Text, Text> {

        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            HashMap<String, Integer> hashMap = new HashMap<String, Integer>();
            Iterator<Text> vals = values.iterator();
            while (vals.hasNext()) {
                // value for each line is docId (key = word, value = docId)
                String docId = vals.next().toString();

                // get count for each docId from hashMap
                Integer currentCount = hashMap.get(docId);

                // update count for each docId
                if (currentCount == null) {
                    hashMap.put(docId, 1);
                } else {
                    currentCount = currentCount + 1;
                    hashMap.put(docId, currentCount);
                }
            }

            // set output format
            boolean isFirst = true;
            StringBuilder toReturn = new StringBuilder();
            for (Map.Entry<String, Integer> entry : hashMap.entrySet()) {
                if (!isFirst) {
                    toReturn.append("\t");
                }
                isFirst = false;
                toReturn.append(entry.getKey()).append(":").append(entry.getValue());
            }

            context.write(key, new Text(toReturn.toString()));
        }
    }

    public static void mapReduceIndex(Path inPath, Path outPath) throws IOException, InterruptedException {

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "TinyGoogle");
        job.setJarByClass(TinyGoogle.class);
        job.setMapperClass(InvertedIndexMapper.class);
        job.setReducerClass(InvertedIndexReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, inPath);
        FileOutputFormat.setOutputPath(job, outPath);

        try {
            job.waitForCompletion(true);
        } catch (Exception e) {
            LOG.error("Error wait for job completion");
            LOG.error(e.toString());
            e.printStackTrace();
        }
    }

    public static boolean indexExists() {
        if (new File("tiny_google.indx").isFile()) {
            return true;
        }

        return false;
    }

    public static void buildInvertedIndex(int mode) throws IOException, InterruptedException {
        System.out.println("____________________________________________________________________");
        if (mode == 0) {
            System.out.println("Building new Inverted Index.");
        } else {
            System.out.println("Adding new directory to Inverted Index.");
        }
        System.out.println("____________________________________________________________________");
        Scanner kb = new Scanner(System.in);
        System.out.print("Please enter path of a directory to index:\t");
        inPath = new Path(kb.nextLine());
        if (mode == 0) {
            System.out.print("Please enter output path:\t");
        } else {
            System.out.print("Please enter output path (must be different than before):\t");
        }
        outPath = new Path(kb.nextLine());
        System.out.println("____________________________________________________________________");
        if (mode == 0) {
            System.out.println("\tPlease wait while the inverted index is generated ... ");
        } else {
            System.out.println("\tPlease wait while the inverted index is updated ... ");
        }
        System.out.println("____________________________________________________________________");

        long startTime = System.nanoTime();
        try {
            mapReduceIndex(inPath, outPath);
        } catch(Exception e) {
            LOG.error("Error completing mapReduceIndex()");
            LOG.error(e.toString());
            e.printStackTrace();
        }
        long endTime = System.nanoTime();
        System.out.println("\nBuilding Inverted Index took "+ ((endTime - startTime)/1000000) + " ms.");

        if (mode == 0) {
            getIndexMap(0);
        } else {
            getIndexMap(2);
        }
        System.out.println("____________________________________________________________________");
        return;
    }

    public static void getIndexMap(int mode) throws IOException, InterruptedException {

        Runtime rt = Runtime.getRuntime();
        Process pr;
        String ii;

        if (mode == 1) {
            ii = "tiny_google.indx";
        } else if (indexExists()) {
            pr = rt.exec("hadoop fs -get " + outPath + "/part-r-00000 temp.indx");
            pr.waitFor();
            ii = "temp.indx";
        } else {
            pr = rt.exec("hadoop fs -get " + outPath + "/part-r-00000 tiny_google.indx");
            pr.waitFor();
            ii = "tiny_google.indx";
        }

        try {
            Scanner f = new Scanner(new File(ii));
            while (f.hasNextLine()) {
                String line = f.nextLine();
                StringTokenizer itr = new StringTokenizer(line, " \t\n\f\r");
                String term = itr.nextToken();

                while (itr.hasMoreTokens()) {
                    String[] parts = itr.nextToken().split(":");
                    String doc = parts[0];
                    int freq = Integer.parseInt(parts[1]);

                    if (!invertedIndex.containsKey(term)) {
                        invertedIndex.put(term, new ArrayList<IndexPair>());
                        invertedIndex.get(term).add(new IndexPair(doc, freq));
                    } else {
                        invertedIndex.get(term).add(new IndexPair(doc, freq));
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error completing mapping");
            LOG.error(e.toString());
            e.printStackTrace();
        }

        File temp = new File("temp.indx");
        if (temp.isFile()) {
            temp.delete();
        }

        if (mode == 2) {
            outputMap();
        }

    }

    public static void outputMap() throws IOException {
        StringBuilder toOutput = new StringBuilder();
        int i = 0;
        for (Map.Entry<String,ArrayList<IndexPair>> entry : invertedIndex.entrySet()){
            // iterate over the entries
            String key = entry.getKey();
            toOutput.append(key + "\t");

            ArrayList<IndexPair> list = entry.getValue();
            int j = 0;
            for (IndexPair pair: list) {
                // iterate over the pairs
                toOutput.append(pair.getKey() + ":" + pair.getValue());
                if (j++ != list.size() - 1) {
                    toOutput.append("\t");
                }
            }

            if (i++ != invertedIndex.size() - 1) {
                toOutput.append("\n");
            }

        }

        new File("tiny_google.indx").delete();
        File file = new File("tiny_google.indx");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.append(toOutput);
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            if (writer != null) writer.close();
        }

    }

    public static void search() {
        Scanner kb = new Scanner(System.in);
        System.out.print("Please enter a search query:\t");
        String response = kb.nextLine();
        System.out.println("____________________________________________________________________");
        System.out.println("\tPlease wait while the search is completed ... ");
        System.out.println("____________________________________________________________________");
        String split[] = response.trim().replaceAll("\\p{Punct}", "").toLowerCase().split("\\s+");

        long startTime = System.nanoTime();
        HashMap<String, Integer> resultDict = new HashMap<String, Integer>();

        for (int i = 0; i < split.length; i++) {
            String term = split[i];
            boolean isPresent = true;
            ArrayList<IndexPair> search = new ArrayList<IndexPair>();
            try {
                search = (ArrayList<IndexPair>)((ArrayList<IndexPair>) invertedIndex.get(term)).clone();
            } catch (Exception e) {
                // System.out.println("Term \""+ term +"\" could not be found in any document.");
                // term not found in any document
                isPresent = false;
            }

            if (isPresent) {
                System.out.println("____________________________________________________________________");
                while (!search.isEmpty()) {
                    IndexPair result = search.remove(0);
                    String docId = result.getKey();
                    Integer docTermCount = result.getValue();
                    Integer currentCount = resultDict.getOrDefault(docId, 0);

                    resultDict.put(docId, currentCount + docTermCount);
                }
            }
        }

        if (!resultDict.isEmpty()) {
            Object[] objArray = resultDict.keySet().toArray();
            String[] keys = Arrays.copyOf(objArray, objArray.length, String[].class);
            ArrayList<IndexPair> list = new ArrayList<IndexPair>();
            for (int i = 0; i < keys.length; i++) {
                list.add(new IndexPair(keys[i], resultDict.get(keys[i])));
            }

            Collections.sort(list, Collections.reverseOrder());

            int numResults = list.size();
            if (numResults > 15) {
                numResults = 15;
            }

            System.out.println("\tTop " + numResults + " Results:");
            System.out.println("____________________________________________________________________");
            for (int i = 0; i < list.size() && i < 15; i++) {
                System.out.println(Integer.toString(i + 1) + ".\t" + list.get(i).getKey());
            }
        } else {
            System.out.println("Your query returned no results!");
        }
        long endTime = System.nanoTime();
        System.out.println("\nSearch took. "+ ((endTime - startTime)/1000000) + " ms.");

        System.out.println("____________________________________________________________________");
    }

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
        if (!indexExists()) {
            while (true) {
                Configuration conf = new Configuration();
                Job job = Job.getInstance(conf, "TinyGoogle");
                job.setJarByClass(TinyGoogle.class);
                job.setMapperClass(InvertedIndexMapper.class);
                job.setReducerClass(InvertedIndexReducer.class);
                job.setOutputKeyClass(Text.class);
                job.setOutputValueClass(Text.class);
                FileInputFormat.setInputDirRecursive(job, true);
                FileInputFormat.addInputPath(job, new Path(args[0]));
                FileOutputFormat.setOutputPath(job, new Path(args[1]));
                System.exit(job.waitForCompletion(true) ? 0 : 1);
                break;
            }
        }
        else {
            while (true) {
                new File("tiny_google.indx").delete();
                Configuration conf = new Configuration();
                Job job = Job.getInstance(conf, "TinyGoogle");
                job.setJarByClass(TinyGoogle.class);
                job.setMapperClass(InvertedIndexMapper.class);
                job.setReducerClass(InvertedIndexReducer.class);
                job.setOutputKeyClass(Text.class);
                job.setOutputValueClass(Text.class);
                FileInputFormat.setInputDirRecursive(job, true);
                FileInputFormat.addInputPath(job, new Path(args[0]));
                FileOutputFormat.setOutputPath(job, new Path(args[1]));
                System.exit(job.waitForCompletion(true) ? 0 : 1);
                break;
            }

        }
    }
}
