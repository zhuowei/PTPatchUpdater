package net.zhuoweizhang.ptpatchupdater;

import java.io.*;

import java.awt.*;

import java.nio.charset.Charset;

import nl.lxtreme.binutils.elf.*;

import com.joshuahuelsman.patchtool.*;

public class PTPatchUpdater {
	
	File origLibFile, newLibFile;
	Elf origLib, newLib;
	Symbol[] origSyms, newSyms;
	
	public PTPatchUpdater(File origLibFile, File newLibFile) {
		this.origLibFile = origLibFile;
		this.newLibFile = newLibFile;
	}
	
	public void loadSymbols() throws IOException {
		origLib = new Elf(origLibFile);
		origLib.loadSymbols();
		newLib = new Elf(newLibFile);
		newLib.loadSymbols();
		origSyms = origLib.getDynamicSymbols();
		newSyms = newLib.getDynamicSymbols();
		
	}
		

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if (args.length == 0) {
				runGui();
				return;
			}
			File ptpatch = new File(args[0]);
			File origLib = new File(args[1]);
			File newLib = new File(args[2]);
			File newPatch = new File(args[3]);
			PTPatchUpdater main = new PTPatchUpdater(origLib, newLib);
			main.loadSymbols();
			main.updatePatch(ptpatch, newPatch);
		} catch (Exception e) {
			System.err.println("OH NOEZ U BROKE IT");
			e.printStackTrace();
		}

	}
	
	public static void runGui() throws Exception {
		Frame frame = new Frame("PTPatchUpdater");
		frame.setVisible(true);
		FileDialog oldLibDialog = new FileDialog(frame, "Select old libminecraftpe.so");
		oldLibDialog.setVisible(true);
		File origLib = new File(oldLibDialog.getDirectory(), oldLibDialog.getFile());
		FileDialog newLibDialog = new FileDialog(frame, "Select new libminecraftpe.so");
		newLibDialog.setVisible(true);
		File newLib = new File(newLibDialog.getDirectory(), newLibDialog.getFile());
		PTPatchUpdater main = new PTPatchUpdater(origLib, newLib);
		main.loadSymbols();
		for (;;) {
			FileDialog oldPatchDialog = new FileDialog(frame, "Select old patch");
			oldPatchDialog.setVisible(true);
			String oldPath = oldPatchDialog.getFile();
			if (oldPath == null) break;
			File oldPatch = new File(oldPatchDialog.getDirectory(), oldPath);
			FileDialog newPatchDialog = new FileDialog(frame, "Save new patch", FileDialog.SAVE);
			newPatchDialog.setVisible(true);
			String newPath = newPatchDialog.getFile();
			if (newPath == null) break;
			File newPatch = new File(newPatchDialog.getDirectory(), newPath);
			main.updatePatch(oldPatch, newPatch);
		}
		frame.setVisible(false);
		System.exit(0);
		
	}
	
	public void updatePatch(File ptpatch, File newPatch) throws IOException {
		PTPatch patch = new PTPatch(ptpatch.getAbsolutePath());
		patch.loadPatch();
		byte[][] newPatchData = new byte[patch.getNumPatches()][];
		for(patch.count = 0; patch.count < patch.getNumPatches(); patch.count++){
			int addr = patch.getNextAddr();
			byte[] data = patch.getNextData();
			System.out.println(addr);
			Symbol modifiedSym = null;
			for (Symbol sym : origSyms) {
				long symAddr = sym.getValue() & ~1L;
				if (symAddr <= addr && symAddr + sym.getSize() >= addr + data.length) {
					System.out.println(sym.toString() + ":" + sym.getValue() + ":" + sym.getSize());
					modifiedSym = sym;
					break;
				}
			}
			int newAddr = getNewAddr(modifiedSym, addr, data);
			System.out.println(newAddr);
			newPatchData[patch.count] = createPTPatchSegment(newAddr, data);
		}
		writePTPatch(newPatch, newPatchData, patch.getMetaData());
	}
	
	public int getNewAddr(Symbol modifiedSym, int addr, byte[] data) {
		Symbol newSym = null;
		String modifiedSymName = modifiedSym.getName();
		for (Symbol sym: newSyms) {
			if (sym.getName().equals(modifiedSymName)) {
				System.out.println(sym.toString() + ":" + sym.getValue() + ":" + sym.getSize());
				newSym = sym;
				break;
			}
		}
		long oldSymAddr = modifiedSym.getValue() & ~1L;
		long newSymAddr = newSym.getValue() & ~1L;
		return (int) (addr + (newSymAddr - oldSymAddr));
	}
	
	public static byte[] createPTPatchSegment(int addr, byte[] data) {
		byte[] retval = new byte[data.length + 4];
		byte[] addrBytes = com.joshuahuelsman.patchtool.Main.intToByteArray(addr);
		System.arraycopy(addrBytes, 0, retval, 0, 4);
		System.arraycopy(data, 0, retval, 4, data.length);
		return retval;
	}
	
	public static void writePTPatch(File out, byte[][] patchData, byte[] metaData) throws IOException {
		out.delete();
		OutputStream os = new FileOutputStream(out);
		Main.writeMagic(os);
		Main.writeVersionCode(0, os);
		Main.writeNumberPatches(patchData.length, os);
		os.write((Main.generateIndices(patchData, (metaData != null? metaData.length : 0))));
		if (metaData != null) os.write(metaData);
		for(int i = 0; i < patchData.length; i++){
			os.write(patchData[i]);
		}
		os.close();
	}

}
