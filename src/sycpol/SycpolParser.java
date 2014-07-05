package sycpol;

import sycpol.Sycpol;
import sycpol.SycpolInterpreter;

import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

public class SycpolParser {

    private static final String EMPTY = "                      ";

    public static final String STRING8 = "STRING 8";
    public static final String STRING16 = "STRING 16";
    public static final String STRING32 = "STRING 32";
    public static final String INTEGER8 = "INTEGER 8";
    public static final String INTEGER16 = "INTEGER 16";

    public static final String STREAM = "STREAM";
    public static final String IOSTREAM = "IO STREAM";
    public static final String OSTREAM = "OUTPUT STREAM";
    public static final String ISTREAM = "INPUT STREAM";

    public static String[] datatypes = {
	STRING8, STRING16, STRING32,
	INTEGER8, INTEGER16,
	IOSTREAM, ISTREAM, OSTREAM, STREAM
    };

    // Parses the type at the end of a line.
    protected String endOf(String line) {
	String dt = "";
	for (String datatype : datatypes) {
	    if (line.endsWith(datatype)) {
		dt = datatype;
		break;
	    }
	}
	if (dt.equals("")) {
	    if (line.lastIndexOf("%") != -1)
		dt = line.substring(line.lastIndexOf("%"), line.length());
	    else Sycpol.exit("TYPE ERROR: UNSUPPORTED TYPE. "+line);
	}
	String dtl = "+" + dt;
	while (line.endsWith(dtl)) {
	    dt = dtl;
	    dtl = "+" + dtl;
	}

	return dt;
    }

    protected String startOf(String line, String dt) {
	return line.substring(0, 22-dt.length()-1).trim();
    }

    protected void skipEmpties(Queue<String> lines) {
	while (lines.size() != 0
	       && (lines.peek().equals(EMPTY)
		   || lines.peek().length() == 0)) lines.poll();
    }

    private static int counter = 1001;

    public class Program {
	public HashMap<String, Module> modules = new HashMap<>();

	public Program parse(Queue<String> in) {
	    skipEmpties(in);
	    while (in.size() != 0) {
		if (in.peek().endsWith("MODULE")) {
		    String name = startOf(in.poll(), "MODULE");
		    Module v
			= this.modules.get(name) != null
			? this.modules.get(name)
			: new Module(name);
		    skipEmpties(in);
		    v.parse(in);
		    v.validate();
		    this.modules.put(name, v);
		    skipEmpties(in);
		}
		else if (in.peek().equals("DOCUMENTATION DIVISION")) {
		    Module b = new Module("ANONYMOYSMODULE@"+counter++);
		    b.parse(in);
		    b.validate();
		    this.modules.put(b.name, b);
		    skipEmpties(in);
		}
		else break;
	    }
	    // System.out.println(in);
	    if (in.size() != 0) {
		Sycpol.exit("SYNTAX ERROR: ILLEGAL JUNK.", "SIZE: " + in.size(), "JUNK: " + in.toString());
	    }
	    return this;
	}
    }

    public class Module {
	public Map<String, Procedure> procedures = new HashMap<>();
	public Map<String, Structure> structures = new HashMap<>();
	public List<Variable> variables = new ArrayList<>();
	public Map<String, ExternalProcedure> externalProcedures
	    = new HashMap<>();
	public Map<String, ExternalStructure> externalStructures
	    = new HashMap<>();
	public List<Declaration> info = new ArrayList<>();
	public String name;

	public boolean out = true, in = true;

	Module(String name) {
	    this.name = name;
	}

	Module parse(Queue<String> in) {
	    String div = "";

	    ArrayList<String> divisions = new ArrayList<>();
	    if (in.size() != 0 && in.peek().endsWith("PROCEDURE")) {
		parseProcedures(in);
	    }
	    else if (in.size() != 0 && in.peek().endsWith("STRUCTURE")) {
		parseStructures(in);
	    }

	    while (in.size() != 0 && in.peek().endsWith("DIVISION")) {
		if (startOf(in.peek(), "DIVISION").equals("DOCUMENTATION")
		    && divisions.contains("DOCUMENTATION"))
		    return this;
		div = startOf(in.poll(), "DIVISION");
		skipEmpties(in);
		if (div.equals("DOCUMENTATION")
		    // NOTE: This breaks full compatibility
		    // with RS-SYCPOL.
		    && !divisions.contains("DOCUMENTATION")) {
		    while (!in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("MODULE")) {
			this.info.add(new Declaration().parse(in));
			skipEmpties(in);
		    }
		}
		if (div.equals("INPUT OUTPUT")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("MODULE")) {
			if (in.peek().startsWith("OPEN")) {
			    // This declaration is completely insane
			    String line = in.poll();
			    if (line.startsWith("OPEN STANDARD INPUT")) {
				this.in = true;
				if (Sycpol.STANDARD_INPUT == null)
				    Sycpol.exit("IO ERROR: UNABLE TO"
						+ "OPEN STANDARD INPUT.");
			    }
			    else if (line.startsWith("OPEN STANDARD OUTPUT")) {
				this.out = true;
				if (Sycpol.STANDARD_OUTPUT == null)
				    Sycpol.exit("IO ERROR: UNABLE TO"
						+ "OPEN STANDARD OUTPUT.");
			    }
			    else Sycpol.exit("SYNTAX ERROR: UNKNOWN STREAM.");
			    skipEmpties(in);
			    continue;
			}
			if (in.peek().startsWith("CLOSE")) {
			    String line = in.poll();
			    if (line.startsWith("CLOSE STANDARD INPUT"))
				this.in = false;
			    else if (line.startsWith("CLOSE STANDARD OUTPUT"))
				this.out = false;
			    else Sycpol.exit("SYNTAX ERROR: UNKNOWN STREAM.");
			    skipEmpties(in);
			    continue;
			}
			Variable v = (Variable) new Variable().parse(in);
			if (v.name.startsWith("$"))
			    Sycpol.exit("SYNTAX ERROR: MUTABLE IO STREAM.");
			this.variables.add(v);
			skipEmpties(in);
		    }
		}
		if (div.equals("STORAGE")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("MODULE")) {
			Variable v = (Variable) new Variable().parse(in);
			this.variables.add(v);
			skipEmpties(in);
		    }
		}
		if (div.equals("IMPORTS")) {
		    while (in.size() != 0
			   && (in.peek().endsWith("EXTERNAL PROCEDURE")
			       || in.peek().endsWith("EXTERNAL STRUCTURE"))) {
			if (in.peek().endsWith("EXTERNAL PROCEDURE")) {
			    String name
				= startOf(in.poll(), "EXTERNAL PROCEDURE");
			    ExternalProcedure v
				= this.externalProcedures.get(name) != null
				? this.externalProcedures.get(name)
				: new ExternalProcedure(name);
			    skipEmpties(in);
			    v.parse(in);
			    v.validate();
			    this.externalProcedures.put(name, v);
			    skipEmpties(in);
			}
			if (in.peek().endsWith("EXTERNAL STRUCTURE")) {
			    String name
				= startOf(in.poll(), "EXTERNAL STRUCTURE");
			    ExternalStructure v
				= this.externalStructures.get(name) != null
				? this.externalStructures.get(name)
				: new ExternalStructure(name);
			    skipEmpties(in);
			    v.parse(in);
			    v.validate();
			    this.externalStructures.put(name, v);
			    skipEmpties(in);
			}
		    }
		}
		if (div.equals("LAYOUT")) {
		    parseStructures(in);
		}
		if (div.equals("PROGRAM CODE")) {
		    parseProcedures(in);
		}
		divisions.add(div);
	    }
	    return this;
	}
	void parseProcedures(Queue<String> in) {
	    while (in.size() != 0
		   && in.peek().endsWith("PROCEDURE")) {
		String name = startOf(in.poll(), "PROCEDURE");
		Procedure v
		    = this.procedures.get(name) != null
		    ? this.procedures.get(name)
		    : new Procedure(name);
		skipEmpties(in);
		if (name.equals("MAIN"))
		    v.parseStatements(in);
		else
		    v.parse(in);
		v.validate();
		this.procedures.put(name, v);
		skipEmpties(in);
	    }
	}
	void parseStructures(Queue<String> in) {
	    while (in.size() != 0
		   && in.peek().endsWith("STRUCTURE")) {
		String name = startOf(in.poll(), "STRUCTURE");
		Structure v
		    = this.structures.get(name) != null
		    ? this.structures.get(name)
		    : new Structure(name);
		skipEmpties(in);
		v.parse(in);
		v.validate();
		this.structures.put(name, v);
		skipEmpties(in);
	    }
	}
	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING MODULE NAME.");
	    if (!Character.isLetter(this.name.charAt(0)))
		Sycpol.exit("SYNTAX ERROR: ILLEGAL MODULE NAME.");
	}
    }

    public static Declaration getDeclaration(List<Declaration> info, String name) {
	for (Declaration d : info) if (d.name.equals(name)) return d;
	return null;
    }

    public class Declaration {
	public String name;
	public String datatype;

	public String value = "";

	Declaration parse(Queue<String> in) {
	    String line = in.poll();
	    this.datatype = endOf(line);
	    this.name = startOf(line, this.datatype);
	    while (in.size() != 0 && !in.peek().equals(EMPTY)) {
		value = value + in.poll() + "\n";
	    }
	    //System.err.println(this.name + ":" + this.value);
	    validate();
	    return this;
        }
	
	public SycpolInterpreter.SycpolObject
	    createObject(SycpolInterpreter inter) {
	    if (this.value.trim().equals("UNDEFINED")
		|| this.value.trim().equals(""))
		return null;

	    if (this.datatype.equals(STRING8)
		|| this.datatype.equals(STRING16)
		|| this.datatype.equals(STRING32)) {
		String newval = "";
		for (String val : this.value.split("\n")) {
		    val = val.trim();
		    if (!val.startsWith("\"") || !val.endsWith("\""))
			Sycpol.exit("SYNTAX ERROR: INVALID STRING VALUE.", "VAL: " + val);
		    for (int i = 1; i < val.length()-1; i++) {
			char chr = val.charAt(i);
			if (chr == '\\') {
			    switch (val.charAt(i++)) {
			    case '"':
				newval += '"';
				break;
			    }
			} else if (chr == '"')
			    Sycpol.exit("SYNTAX ERROR: INVALID STRING VALUE.", "VAL: " + val);
			else newval += chr;
		    }
		}
		return inter.new SycpolObject(this.datatype, newval);
	    }

	    if (this.datatype.equals(INTEGER8)
		|| this.datatype.equals(INTEGER16)) {
		String str = this.value.trim();
		if (!str.matches("^[0-9]+$")) {
		    Sycpol.exit("SYNTAX ERROR: INVALID INTEGER VALUE.", "VAL: " + str);
		}
		try {
		    switch (this.datatype) {
		    case INTEGER8:
			return inter.new SycpolObject((byte)
						      Byte.parseByte(str));
		    case INTEGER16:
			return inter.new SycpolObject((short)
						      Short.parseShort(str));
		    }
		} catch (Exception ex) {
		    Sycpol.exit("SYNTAX ERROR: INVALID INTEGER VALUE.", "VAL: " + str);
		    return null;
		}
	    }

	    if (this.datatype.equals(IOSTREAM)
		|| this.datatype.equals(ISTREAM)
		|| this.datatype.equals(OSTREAM)) {
		OutputStream outs = null;
		InputStream ins = null;

		String[] ss = this.value.split("\n");
		for (String s : ss) {
		    if (s.startsWith("OUTS")) {
			String stream = s.substring(5).trim();
			if (stream.equals("STANDARD OUTPUT")) {
			    outs = Sycpol.STANDARD_OUTPUT;
			}
			else if (stream.startsWith("FS")) {
			    String path = stream.substring(3);
			    try {
				outs = new FileOutputStream(new File(path));
			    } catch (IOException ex) {
				Sycpol.exit("IO ERROR: FS FAILURE.", "PATH: " + path, "JAVA ERROR: " + ex.toString().toUpperCase());
			    }
			}
		    }
		    if (s.startsWith("INS")) {
			String stream = s.substring(4).trim();
			if (stream.equals("STANDARD INPUT")) {
			    ins = Sycpol.STANDARD_INPUT;
			}
			else if (stream.startsWith("FS")) {
			    String path = stream.substring(3);
			    try {
				ins = new FileInputStream(new File(path));
			    } catch (IOException ex) {
				Sycpol.exit("IO ERROR: FS FAILURE.", "PATH: " + path, "JAVA ERROR: " + ex.toString().toUpperCase());
			    }
			}
		    }
		}
		return inter.new SycpolObject(this.datatype, ins, outs);
	    }
	    return inter.new SycpolObject(this.datatype);
	}

	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING DECLARATION NAME.", "TYPE: " + this.datatype);
	    if (!Character.isLetter(this.name.charAt(0)))
		Sycpol.exit("SYNTAX ERROR: ILLEGAL DECLARATION NAME.", "NAME: " + this.name, "TYPE: " + this.datatype);
	}
    }
    public class Variable extends Declaration {
	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING VARIABLE NAME.", "TYPE: " + this.datatype);
	    if (!this.name.startsWith("$") && !this.name.startsWith("."))
		Sycpol.exit("SYNTAX ERROR: INVALID VARIABLE PREFIX.", "NAME: " + this.name, "TYPE: " + this.datatype);
	    if (this.name.startsWith(".") && this.value == null)
		Sycpol.exit("SYNTAX ERROR: MISSING CONSTANT VALUE.", "NAME: " + this.name, "TYPE: " + this.datatype);
	}
    }
    public class Parameter extends Declaration {
	Declaration parse(Queue<String> in) {
	    String line = in.poll();
	    this.datatype = endOf(line);
	    this.name = startOf(line, this.datatype);
	    validate();
	    return this;
        }

	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING PARAMETER NAME.", "TYPE: " + this.datatype);
	    if (!this.name.startsWith("$") && !this.name.startsWith(".")) 
		Sycpol.exit("SYNTAX ERROR: INVALID PARAMETER PREFIX.", "NAME: " + this.name, "TYPE: " + this.datatype);
	}
    }
    public class Field extends Declaration {
	Declaration parse(Queue<String> in) {
	    String line = in.poll();
	    this.datatype = endOf(line);
	    this.name = startOf(line, this.datatype);
	    validate();
	    return this;
        }

	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING FIELD NAME.", "TYPE: " + this.datatype);
	}
    }

    public class ExternalProcedure {
	public String name;
	public List<Declaration> info = new ArrayList<>();

	ExternalProcedure(String name) {
	    this.name = name;
	}

	ExternalProcedure parse(Queue<String> in) {
	    while (in.size() != 0
		   && !in.peek().endsWith("DIVISION")
		   && !in.peek().endsWith("MODULE")) {
		this.info.add(new Declaration().parse(in));
		skipEmpties(in);
	    }
	    return this;
	}

	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING PROCEDURE NAME.");
	    if (!name.startsWith("!")) {
		Sycpol.exit("SYNTAX ERROR: INVALID PROCEDURE PREFIX.", "NAME: " + this.name);
	    }
	}
    }

    public class ExternalStructure {
	public String name;
	public List<Declaration> info = new ArrayList<>();

	ExternalStructure(String name) {
	    this.name = name;
	}

	ExternalStructure parse(Queue<String> in) {
	    while (in.size() != 0
		   && !in.peek().endsWith("DIVISION")
		   && !in.peek().endsWith("MODULE")) {
		this.info.add(new Declaration().parse(in));
		skipEmpties(in);
	    }
	    return this;
	}

	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING STRUCTURE NAME.");
	    if (!name.startsWith("%")) {
		Sycpol.exit("SYNTAX ERROR: INVALID STRUCTURE PREFIX.", "NAME: " + this.name);
	    }
	}
    }

    public class Procedure {
	public String name;
	public List<Declaration> info = new ArrayList<>();
	public List<Parameter> parameters = new ArrayList<>();
	public List<Variable> variables = new ArrayList<>();
	public Map<Short, String> statements = new HashMap<>();

	Procedure(String name) {
	    this.name = name;
	}

	Procedure parse(Queue<String> in) {
	    while (in.size() != 0
		   && !in.peek().endsWith("DIVISION")
		   && !in.peek().endsWith("SECTION")
		   && !in.peek().endsWith("MODULE")) {
		this.info.add(new Declaration().parse(in));
		skipEmpties(in);
	    }
	    while (in.size() != 0 && in.peek().endsWith("SECTION")) {
		String sec = startOf(in.poll(), "SECTION");
		skipEmpties(in);
		if (sec.equals("PARAMETER")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("SECTION")
			   && !in.peek().endsWith("MODULE")) {
			this.parameters.add((Parameter) new Parameter()
					    .parse(in));
			skipEmpties(in);
		    }
		    skipEmpties(in);
		}
		if (sec.equals("VARIABLE")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("SECTION")
			   && !in.peek().endsWith("MODULE")) {
			this.variables.add((Variable) new Variable().parse(in));
			skipEmpties(in);
		    }
		}
		if (sec.equals("DOCUMENTATION")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("SECTION")
			   && !in.peek().endsWith("MODULE")) {
			this.info.add(new Declaration().parse(in));
			skipEmpties(in);
		    }
		}
		if (sec.equals("PROCEDURE CODE")) parseStatements(in);
	    }
	    parseStatements(in);
	    return this;
	}
	void parseStatements(Queue<String> in) {
	    while (in.size() != 0
		   && !in.peek().endsWith("DIVISION")
		   && !in.peek().endsWith("SECTION")
		   && !in.peek().endsWith("MODULE")
		   && in.peek().startsWith("(")) {
		String stmt = "";
		String line = in.poll();
		String strnum = line.substring(1, line.indexOf(")"));
		short num = -1;
		try {
		    num = Short.parseShort(strnum);
		} catch (NumberFormatException ex) {
		    Sycpol.exit("SYNTAX ERROR: INVALID STATEMENT.", "STATEMENT: " + line);
		}
		stmt = line.substring(line.indexOf(")")+2).trim();
		skipEmpties(in);
		while (in.size() != 0 && in.peek().startsWith("  ")) {
		    stmt += " " + in.poll().trim();
		    skipEmpties(in);
		}
		statements.put(num, stmt);
	    }
	}
	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING PROCEDURE NAME.");
	    if (!name.startsWith("!") && !name.equals("MAIN")) {
		Sycpol.exit("SYNTAX ERROR: INVALID PROCEDURE PREFIX.", "NAME: " + this.name);
	    }
	}
    }

    public class Structure {
	public String name;
	public List<Field> fields = new ArrayList<>();
	public List<Declaration> info = new ArrayList<>();

	Structure(String name) {
	    this.name = name;
	}

	Structure parse(Queue<String> in) {
	    while (in.size() != 0
		   && !in.peek().endsWith("DIVISION")
		   && !in.peek().endsWith("SECTION")
		   && !in.peek().endsWith("MODULE")) {
		this.fields.add((Field) new Field().parse(in));
		skipEmpties(in);
	    }
	    while (in.size() != 0 && in.peek().endsWith("SECTION")) {
		String sec = startOf(in.poll(), "SECTION");
		skipEmpties(in);
		if (sec.equals("DOCUMENTATION")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("SECTION")
			   && !in.peek().endsWith("MODULE")) {
			this.info.add(new Declaration().parse(in));
			skipEmpties(in);
		    }
		}
		if (sec.equals("FIELD")) {
		    while (in.size() != 0
			   && !in.peek().endsWith("DIVISION")
			   && !in.peek().endsWith("SECTION")
			   && !in.peek().endsWith("MODULE")) {
			this.fields.add((Field) new Field().parse(in));
			skipEmpties(in);
		    }
		}
	    }
	    return this;
	}
	public void validate() {
	    if (this.name.trim().length() == 0)
		Sycpol.exit("SYNTAX ERROR: MISSING STRUCTURE NAME.");
	    if (!name.startsWith("%")) {
		Sycpol.exit("SYNTAX ERROR: INVALID STRUCTURE PREFIX.", "NAME: " + this.name);
	    }
	}
    }
}
