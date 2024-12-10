package il.ac.kinneret.SWReceiver;


public class SlidingWindowReceiver {
    public static void main(String[] args) {

        String ip = null, outfile = null;
        int port = -1, rws = -1;

        for (String arg : args) {
            if (arg.startsWith("-ip=")) {
                ip = arg.substring(4);
            } else if (arg.startsWith("-port=")) {
                port = Integer.parseInt(arg.substring(6));
            } else if (arg.startsWith("-outfile=")) {
                outfile = arg.substring(9);
            } else if (arg.startsWith("-rws=")) {
                rws = Integer.parseInt(arg.substring(5));
            }
        }


    }
}