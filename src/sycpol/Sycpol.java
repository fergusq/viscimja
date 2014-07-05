package sycpol;

import sycpol.CardParser;
import sycpol.SycpolParser;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.IOException;

public class Sycpol {

    public static final String VERSION = "0.1";

    public static OutputStream STANDARD_OUTPUT = System.out;
    public static InputStream STANDARD_INPUT = System.in;
    
    public static void exit(String msg, String... more) {
	try {
	    String error = msg.split(":")[0];
	    String str = msg.split(":")[1].trim();
	    System.err.println(error+":");
	    while (str.length() > 22) {
		String line = str.substring(0, 21);
		System.err.println(line+"\\");
		str = str.substring(21).trim();
	    }
	    System.err.println(str);

	    for (String m : more) System.err.println(m);

	    System.exit(1);
	} catch (ArrayIndexOutOfBoundsException ex) {
	    System.err.println("-> SCIM ERROR\nILLEGAL BUG.");
	    System.exit(1);
	}
    }
    
    public static List<String> readFile(String path) throws IOException {
	return Files.readAllLines(Paths.get(path), StandardCharsets.US_ASCII);
    }

    public static void loadFile(String filepath, CardParser cards) throws IOException {
	File file = new File(filepath);
	if (!file.isDirectory())
	    Sycpol.exit("IO ERROR: NOT A FILE.",
			"NAME: " + filepath, "PATH: " + file.getAbsolutePath());

	ArrayList<String> acards = new ArrayList<>(Arrays.asList(file.list()));
	for (int i = 0;;i++) {
	    if (acards.contains(""+i))
		cards.loadCard(readFile(file.getAbsolutePath()+"/"+i));
	    else break;
	}
    }

    public static boolean verbose = false, debug = false;

    public static void main(String... args) {
	boolean checkCardsOnly=false, checkDeclarationsOnly=false;
	
	try {
	    CardParser cards = new CardParser();
	    
	    for (int i = 0; i < args.length; i++) {
		switch (args[i]) {
		case "--check-cards":
		    checkCardsOnly = true;
		    break;
		case "--check-declarations":
		    checkDeclarationsOnly = true;
		    break;
		case "-V":
		case "--verbose":
		    verbose = true;
		    break;
		case "--debug":
		    debug = true;
		    break;
		case "-h":
		case "--help":
		    System.out.println("SYCPOL COMPILER.");
		    return;
		case "-v":
		case "--version":
		    System.out.println(VERSION);
		    return;
		case "-f":
		    String file = args[++i];
		    try {
			loadFile(file, cards);
		    } catch(IOException ex) {
			exit("IO ERROR: " + ex.toString().toUpperCase());
		    }
		    break;
		case "-t":
		case "--tape":
		    try {
			cards.loadTape(readFile(args[++i]));
		    } catch(IOException ex) {
			exit("IO ERROR: " + ex.toString().toUpperCase(), "NAME: " + args[i]);
		    }
		    break;
		default:
		    try {
			cards.loadCard(readFile(args[i]));
		    } catch(IOException ex) {
			exit("IO ERROR: " + ex.toString().toUpperCase(), "NAME: " + args[i]);
		    }
		    break;
		}
	    }
	    
	    if (checkCardsOnly) return;

	    String[] lines = cards.getCode().split("\n");
	    Queue<String> queue = new ArrayBlockingQueue(lines.length);
	    for (String s : lines) {
		if (debug) System.out.println(s);
		queue.offer(s);
	    }
	    
	    if (checkDeclarationsOnly) return;

	    SycpolParser.Program prog = new SycpolParser().new Program();
	    
	    prog.parse(queue);
	    
	    SycpolInterpreter inter = new SycpolInterpreter(prog);
	    inter.interpret();

	    STANDARD_OUTPUT.flush();
	    STANDARD_OUTPUT.write('\n');
	    
	} catch (Exception ex) {
	    System.err.println("SCIM ERROR:\nILLEGAL BUG.");
	    ex.printStackTrace();
	}
    }
}
