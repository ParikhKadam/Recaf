package me.coley.recaf.control.headless;

import me.coley.recaf.Recaf;
import me.coley.recaf.command.impl.Disassemble;
import me.coley.recaf.parse.bytecode.*;
import me.coley.recaf.parse.bytecode.ast.RootAST;
import me.coley.recaf.util.ClassUtil;
import org.apache.commons.io.FileUtils;
import org.jline.builtins.Nano;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.coley.recaf.util.Log.*;

/**
 * A utility using JLine to interactively editing varied content.
 *
 * @author Matt
 */
public class JLineEditor {
	private final Terminal terminal;

	/**
	 * Create a editor with the JLine terminal.
	 *
	 * @param terminal Terminal to work off of.
	 */
	public JLineEditor(Terminal terminal) {
		this.terminal = terminal;
	}

	/**
	 * Open an editor with the given title / file to load from. Blocks until the editor is closed.
	 *
	 * @param title
	 * 		Editor title.
	 * @param file
	 * 		File containing content to read/write to.
	 *
	 * @throws IOException
	 * 		Thrown if the file cannot be opened.
	 */
	private void openEditor(String title, File file) throws IOException {
		Nano nano = new Nano(terminal, file);
		nano.title = title;
		nano.open(file.getAbsolutePath());
		nano.run();
		terminal.puts(InfoCmp.Capability.clear_screen);
		terminal.flush();
	}

	/**
	 * Open and handle editing disassembled method code.
	 *
	 * @param wrapper
	 * 		Disassembly results.
	 */
	public void open(Disassemble.Result wrapper) {
		try {
			MethodNode mn = wrapper.getMethod();
			ClassNode cn = wrapper.getOwner();
			// Write disassembled text to file
			File tmp = File.createTempFile("recaf", "disass");
			FileUtils.write(tmp, wrapper.getDisassembled(), UTF_8);
			long last = tmp.lastModified();
			// Open editor
			openEditor("Assembler", tmp);
			// Check if any edits occured.
			if (tmp.lastModified() == last)
				return;
			// Assemble modified code
			String code = FileUtils.readFileToString(tmp, UTF_8);
			ParseResult<RootAST> result = Parse.parse(code);
			Assembler assembler = new Assembler(cn.name);
			MethodNode generated = assembler.compile(result);
			// Replace method
			int index = cn.methods.indexOf(mn);
			if(index >= 0) {
				MethodNode old = cn.methods.get(index);
				generated.invisibleAnnotations = old.invisibleAnnotations;
				generated.visibleAnnotations = old.visibleAnnotations;
				generated.invisibleParameterAnnotations = old.invisibleParameterAnnotations;
				generated.visibleParameterAnnotations = old.visibleParameterAnnotations;
				generated.invisibleTypeAnnotations = old.invisibleTypeAnnotations;
				generated.visibleTypeAnnotations = old.visibleTypeAnnotations;
				generated.invisibleLocalVariableAnnotations = old.invisibleLocalVariableAnnotations;
				generated.visibleLocalVariableAnnotations = old.visibleLocalVariableAnnotations;
				cn.methods.set(index, generated);
			}
			else
				throw new IllegalStateException("Failed to replace method, " +
						"modified method no longer exists in the class?");
			byte[] value = ClassUtil.toCode(cn, ClassWriter.COMPUTE_FRAMES);
			Recaf.getCurrentWorkspace().getPrimary().getClasses().put(cn.name, value);
			// Cleanup temp
			tmp.delete();
			info("Updated {}.{}{}", cn.name, mn.name, mn.desc);
		} catch(AssemblerException ex) {
			if (ex.getLine() >= 0)
				error(ex, "Line: " + ex.getLine() + " - " +  ex.getMessage());
			else
				error(ex, ex.getMessage());
		} catch(IOException ex) {
			error(ex, "IO error");
		}
	}
}