import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ycc on 18/10/16.
 */
public class ReadCatalog {

    private Map<String,Set<String>> currIndex = new HashMap<String,Set<String>>();
    private Map<String,List<Span>> wordIndex = new HashMap<String,List<Span>>();
    /**
     * 获取keyWord在content的出现位置,并更新包括corr的文档列表
     * @return
     */
    public List<Integer> insertFile(String keyWord,String fileName) {
        String content = readToString(fileName);
        List<Integer> pos = new ArrayList<Integer>();
        pos = KMPmatcher(content,keyWord);
        if(pos != null || pos.size() != 0 ) {
            Set<String> temp = null;
            if (currIndex.containsKey(keyWord)) {
                temp = currIndex.get(keyWord);
            }else {
                temp = new HashSet<String> ();
            }
            temp.add(fileName);
            currIndex.put(keyWord,temp);
        }
        return pos;
    }

    /**
     * 根据文件路径读取文件
     * @param fileName
     * @return
     */
    public static String readToString(String fileName) {
        String encoding = "UTF-8";
        File file = new File(fileName);
        Long filelength = file.length();
        byte[] filecontent = new byte[filelength.intValue()];
        try {
            FileInputStream in = new FileInputStream(file);
            in.read(filecontent);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            return new String(filecontent, encoding);
        } catch (UnsupportedEncodingException e) {
            System.err.println("The OS does not support " + encoding);
            e.printStackTrace();
            return null;
        }
    }

    public static int[] computePrefix(String P) {
        int[] next = new int[P.length()];
        int k = 0;
        next[0] = 0;
        for (int q = 1; q < P.length(); q++) {
            while (k > 0 && P.charAt(k) != P.charAt(q))
                k = next[k];
            if (P.charAt(k) == P.charAt(q))
                k = k + 1;
            next[q] = k;
        }
        return next;
    }

    public static List<Integer> KMPmatcher(String T, String P) {
        List<Integer> res = new ArrayList<Integer>();
        int[] next = new int[P.length()];
        next = computePrefix(P);
        int q = 0;
        for (int i = 0; i < T.length(); i++) {
            while (q > 0 && P.charAt(q) != T.charAt(i))
                q = next[q - 1];
            if (P.charAt(q) == T.charAt(i))
                q = q + 1;
            if (q == P.length()) {
                res.add(i - q + 1);
                q = next[q - 1];
            }
        }
        return res;
    }

    public Set<String> showTream(String doc) throws IOException {
        Analyzer luceneAnalyzer = new IKAnalyzer();// new
        TokenStream tokenStream = luceneAnalyzer.tokenStream("content", new StringReader(doc));
        tokenStream.reset();
        Set<String> keyWords = new HashSet<String>();
        while (tokenStream.incrementToken()) {
            CharTermAttribute charTermAttribute = (CharTermAttribute) tokenStream.getAttribute(CharTermAttribute.class);
            String tword = charTermAttribute.toString();
            keyWords.add(tword);
        }
        tokenStream.end();
        tokenStream.close();
        return keyWords;
    }

    /**
     * 每新产生一个日志文件,就进行处理,暂时只当作静态文件分析,时间够的话,可以使用流计算
     * @param fileName
     * @param keyWords
     */
    public void getRelation(String fileName, String keyWords) {
        String content = readToString(fileName);
        List<Span> newIndexs = getStringByKeyWords(keyWords, content);
        List<Span> list = null;
        if (wordIndex.containsKey(fileName)) {
            list = wordIndex.get(fileName);
        } else {
            list = new ArrayList<Span>();
        }
        list.addAll(newIndexs);
    }

    /**
     * 根据你要的关键字和文章内容进行查找句子
     * @param keyWords
     * @param content
     * @return
     */
    public static List<Span> getStringByKeyWords(String keyWords, String content) {
        List<Span> list = new ArrayList<Span>();
        content = content.replaceAll("[\r\n]+", "");//先把文章中出现换行的先去掉
        Matcher m = Pattern.compile("([^.]*?" + keyWords + ".*?).").matcher(content);
        while (m.find()) {
            String str = m.group(1);
            Matcher newMatcher = Pattern.compile("corr_id=(//s+)&ri=(//s+)&node_id=(//s+)").matcher(str);
            Span span = new Span();
            while (newMatcher.find()) {
                span.corr_id = newMatcher.group(1);
                span.node_id = newMatcher.group(3);
            }
            Matcher matcher2= Pattern.compile("corr_id=(//s+)&node_id=(//s+)&ri=(//s+)").matcher(str);
            while(matcher2.find()) {
                span.ri = matcher2.group(3);
            }
            list.add(span);
        }
        return list;
    }


    public static void main(String [] args) {
        ReadCatalog catalog = new ReadCatalog();
        Map<String,Set<String>> currIndex = catalog.currIndex;
        Map<String,List<Span>> wordIndex = catalog.wordIndex;
        Scanner scanner = new Scanner(System.in);
        String corr_id = scanner.nextLine();
        if (!currIndex.containsKey(corr_id)) {
           System.out.println("this corr_id doesn't exist");
        }
        Set<String> fileNames = currIndex.get(corr_id);
        List<Span> relateSpan = new ArrayList<Span>(); //找到与该corr_id相关的所有Span,从而用来构建链状结构
        for (String fileName : fileNames) {
            List<Span> spans = wordIndex.get(fileName);
            for (int i=0;i<spans.size();i++) {
                Span temp = spans.get(i);
                if (corr_id.equals(temp.corr_id)) {
                    relateSpan.add(temp);
                }
            }
        }
        System.out.println(corr_id+" service link is :");
        for(Span span : relateSpan) {
            System.out.println(span.ri+" to: "+span.node_id);
        }
    }

}
