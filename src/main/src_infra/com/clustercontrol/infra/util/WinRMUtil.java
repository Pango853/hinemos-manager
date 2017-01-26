/*

 Copyright (C) 2014 NTT DATA Corporation

 This program is free software; you can redistribute it and/or
 Modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation, version 2.

 This program is distributed in the hope that it will be
 useful, but WITHOUT ANY WARRANTY; without even the implied
 warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 PURPOSE.  See the GNU General Public License for more details.

 */
package com.clustercontrol.infra.util;

import intel.management.wsman.WsmanException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.clustercontrol.fault.HinemosUnknown;
import com.clustercontrol.infra.bean.ModuleNodeResult;
import com.clustercontrol.infra.bean.OkNgConstant;
import com.clustercontrol.maintenance.util.HinemosPropertyUtil;
import com.clustercontrol.util.XMLUtil;

public class WinRMUtil {
	private static Log log = LogFactory.getLog( WinRMUtil.class );
	
	public static ModuleNodeResult execCommand(String user, String password, String host, int port, String protocol, String command, int maxSize) {
		log.info("user=" + user + ", host=" + host + ", port=" + port + ", command=" + command);
		// log.debug("password=" + password);
		
		WinRs winRs = new WinRs(host, port, protocol, user, password);
		String shellId = null;
		try {
			shellId = winRs.openShell();

			String[] args = {};
			WinRsCommandOutput output = runCommand(winRs, command, args, shellId);
			
			String std = "out=" + output.getStdout().trim();
			String err = "err=" + output.getStderr().trim();
			String msg = String.format("command=%s\n", command);
			msg = msg + String.format("exitCode=%d\n", output.getExitCode());
			msg = msg + std.substring(0, Math.min(std.length(), (maxSize - msg.length() - 1) - Math.min((maxSize - msg.length() - 1) / 2, err.length()))) + "\n";
			msg = msg + err.substring(0, Math.min(err.length(), (maxSize - msg.length() - 1) / 2));

			return new ModuleNodeResult(
					output.getExitCode() == 0 ? OkNgConstant.TYPE_OK : OkNgConstant.TYPE_NG,
							(int) output.getExitCode(),
							XMLUtil.ignoreInvalidString(msg));
		} catch (WsmanException e) {
			WsmanException we = (WsmanException) e;
			log.warn("execCommand " + getMessage(we));
			return createResultFromException(e);
		} catch (Exception e) {
			log.warn("execCommand " + e.getMessage() + "(" + e.getClass().getName() + ")", e);
			return createResultFromException(e);
		} finally {
			closeShell(winRs, shellId);
		}
	}

	public static ModuleNodeResult recvFile(String user, String password, String host, int port, String protocol,
			String srcDir, String srcFilename, String dstDir, String dstFilename, 
			String owner, String perm) {

		WinRs winRs = new WinRs(host, port, protocol, user, password);
		String shellId = null;
		try {
			shellId = winRs.openShell();

			//バイナリファイルを対応するために、ファイルの内容をBase64でエンコードしてから、stdに出力する。
			String command = "powershell";
			String[] args = { 
					"-inputformat",
					"none",
					String.format("[System.convert]::ToBase64String([System.IO.File]::ReadAllBytes('%s'))",
							srcDir + File.separator + srcFilename) };
			WinRsCommandOutput output = runCommand(winRs, command, args, shellId);
			
			//std出力の内容にある改行を消して、元のバイナリに変更する。
			String base64Content = output.getStdout().replaceAll("\n", "");
			base64Content = output.getStdout().replaceAll("\r", "");
			byte[] binaryContent = Base64.decodeBase64(base64Content);
			writeToBinaryFile(dstDir + File.separator + dstFilename, binaryContent);

			return new ModuleNodeResult(OkNgConstant.TYPE_OK, 0, "exitCode=0");
		} catch (WsmanException e) {
			WsmanException we = (WsmanException) e;
			log.warn("recvFile " + getMessage(we));
			return createResultFromException(e);
		} catch (Exception e) {
			log.warn("recvFile " + e.getMessage() + "(" + e.getClass().getName() + ")", e);
			return createResultFromException(e);
		} finally {
			closeShell(winRs, shellId);
		}
	}
	
	public static ModuleNodeResult sendFile(String user, String password, String host, int port, String protocol,
			String srcDir, String srcFilename, String dstDir, String dstFilename,
			String owner, String perm, boolean isBackupIfExistFlg, String account)  {
		
		WinRs winRs = new WinRs(host, port, protocol, user, password);
		String shellId = null;
		try {
			shellId = winRs.openShell();

			String tempFilePath = getTempFilePath(winRs, shellId);
			String dstFilePath = dstDir + File.separator + dstFilename;
			
			//一時ファイルにアップロード
			WinRsCommandOutput output = downloadToTempFile(winRs, shellId, tempFilePath, srcFilename, account);
			if (output.getExitCode() != 0) {
				log.warn("sendFile() code="+output.getExitCode()+",out="+output.getStdout()+",err="+output.getStderr());
				return new ModuleNodeResult(OkNgConstant.TYPE_NG, 1, output.getStderr());
			}
			
			//一時ファイルをディコードして、送信先ファイルに保存
			output = decodeTempFile(winRs, shellId, tempFilePath, dstFilePath, isBackupIfExistFlg);
		
			if (output.getExitCode() != 0) {
				log.warn("sendFile() code="+output.getExitCode()+",out="+output.getStdout()+",err="+output.getStderr());
				return new ModuleNodeResult(OkNgConstant.TYPE_NG, 1, output.getStderr());
			}
			return new ModuleNodeResult(OkNgConstant.TYPE_OK, 0,
					dstFilename + " was transfered. (" + dstDir + ")");
		} catch (WsmanException e) {
			WsmanException we = (WsmanException) e;
			log.warn("sendFile " + getMessage(we), e);
			return createResultFromException(e);
		} catch (Exception e) {
			log.warn("sendFile " + e.getMessage() + "(" + e.getClass().getName() + ")", e);
			return createResultFromException(e);
		} finally {
			closeShell(winRs, shellId);
		}
	}

	public static ModuleNodeResult isSameMd5(String user, String password, String host, int port, String protocol,
			String srcDir, String srcFilename, String dstDir, String dstFilename) {
		ModuleNodeResult ret = new ModuleNodeResult(OkNgConstant.TYPE_NG, -1, "unknown");
		
		FileInputStream srcFis = null;
		WinRs winRs = new WinRs(host, port, protocol, user, password);
		String shellId = null;		
		try {
			shellId = winRs.openShell();

			String srcFilePath = srcDir + File.separator + srcFilename;
			String dstFilePath = dstDir + File.separator + dstFilename;

			srcFis = new FileInputStream(new File(srcFilePath));
			String srcMd5 = DigestUtils.md5Hex(srcFis);
			
			String command = "powershell";
			String[] args = {
					"-inputformat",
					"none",
					"-encodedCommand",
					Base64.encodeBase64String(getMd5Script(dstFilePath).getBytes("UTF-16LE")) };
			
			WinRsCommandOutput output = runCommand(winRs, command, args, shellId);
			String dstMd5 = output.getStdout();
			
			//XX-XX-XX形のMD5をXXXXXに、大文字から小文字に変換
			dstMd5 = dstMd5.trim().replaceAll("-", "").toLowerCase();
			
			if(srcMd5.equals(dstMd5)) {
				ret.setResult(OkNgConstant.TYPE_OK);
				ret.setStatusCode(0);
				ret.setMessage("MD5s are same");
			} else {
				ret.setResult(OkNgConstant.TYPE_NG);
				ret.setStatusCode(0);
				ret.setMessage("MD5s differ : src=" + srcMd5 + ", dst=" + dstMd5);
			}
		} catch (WsmanException e) {
			WsmanException we = (WsmanException) e;
			log.warn("isSameMd5 " + getMessage(we));
			ret.setMessage(e.getMessage());
		} catch (Exception e) {
			log.warn("isSameMd5 : " + e.getMessage(), e);
			ret.setMessage(e.getMessage());
		} finally {
			if (srcFis != null) {
				try {
					srcFis.close();
				} catch (IOException e) {
				}
			}
		}
		
		return ret;
	}

	private static String getMessage(WsmanException we) {
		String str = "";
		try {
			str += we.getCode();
		} catch (Exception e) {}
		str += ",";
		try {
			str += we.getSubCode();
		} catch (Exception e) {}
		str += ",";
		try {
			str += we.getReason();
		} catch (Exception e) {}
		str += ",";
		try {
			str += we.getDetail();
		} catch (Exception e) {}
		
		return str;
	}
	
	private static String getMd5Script(String dstFilePath) {
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("$filePath=\"%s\"\n", dstFilePath));
		buffer.append("$md5=New-Object -TypeName System.Security.Cryptography.MD5CryptoServiceProvider\n");
		buffer.append("$hash=[System.BitConverter]::ToString($md5.ComputeHash([System.IO.File]::ReadAllBytes($filePath)))\n");
		buffer.append("$hash\n");
		
		return buffer.toString();
	}

	private static void closeShell(WinRs winRs, String shellId) {
		if (shellId != null) {
			try {
				winRs.closeShell(shellId);
			} catch (WsmanException e) {
			}
		}
	}
	
	private static ModuleNodeResult createResultFromException(Exception e) {
		String message = null;
		if (e instanceof WsmanException) {
			message = getMessage((WsmanException)e);
		} else {
			message = e.getClass().getName() + ", " + e.getMessage();
		}
		return new ModuleNodeResult(OkNgConstant.TYPE_NG, -1, message);
	}

	private static WinRsCommandOutput decodeTempFile(WinRs winRs, String shellId,
			String tempFilePath, String dstFilePath, boolean isBackupIfExistFlg)
			throws UnsupportedEncodingException, WsmanException {
		
		String command = "powershell";
		String[] args = {
				"-inputformat",
				"none",
				"-encodedCommand",
				Base64.encodeBase64String(getDecodeScript(tempFilePath,
						dstFilePath, isBackupIfExistFlg).getBytes("UTF-16LE")) };
		return runCommand(winRs, command, args, shellId);
	}

	private static String getDecodeScript(String tempFilePath, String dstFilePath, boolean isBackupIfExistFlg) {
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("$tmp_file_path = [System.IO.Path]::GetFullPath('%s')\n", tempFilePath));
		buffer.append(String.format("$dest_file_path = [System.IO.Path]::GetFullPath('%s')\n", dstFilePath));
		buffer.append(String.format("$backup_file_ext = '%s'\n", getBackupFileExt()));
		buffer.append(String.format("$is_backup = $%s\n", isBackupIfExistFlg ? "TRUE" : "FALSE"));
		buffer.append("$dest_dir = ([System.IO.Path]::GetDirectoryName($dest_file_path))\n");
		buffer.append("if (-not(Test-Path $dest_dir)) {\n");
		buffer.append("   echo \"[ERROR] $dest_dir : No such directory\"\n");
		buffer.append("   exit 1\n");
		buffer.append("}\n");
		buffer.append("if (Test-Path $dest_file_path) {\n");
		buffer.append("    if ($is_backup) {\n");
		buffer.append("        Move $dest_file_path \"$dest_file_path.$backup_file_ext\"\n");
		buffer.append("    }\n");
		buffer.append("    rm $dest_file_path\n");
		buffer.append("}\n");
		buffer.append("$xmlDoc = [XML](Get-Content $tmp_file_path)\n");
		buffer.append("$bytes = [System.Convert]::FromBase64String($xmlDoc.Envelope.Body.downloadTransferFileResponse.return)\n");
		buffer.append("[System.IO.File]::WriteAllBytes($dest_file_path, $bytes)\n");
		String str = buffer.toString();
		log.debug(str);
		return str;
	}

	private static String getBackupFileExt() {
		Calendar now = Calendar.getInstance(); 
		return String.format("%04d%02d%02d%02d%02d%02d",
				now.get(Calendar.YEAR),
				now.get(Calendar.MONTH) + 1,
				now.get(Calendar.DAY_OF_MONTH),
				now.get(Calendar.HOUR_OF_DAY),
				now.get(Calendar.MINUTE),
				now.get(Calendar.SECOND));
	}

	private static String getTempFilePath(WinRs winRs, String shellId) throws WsmanException {
		//一時フォルダを取得
		String command = "echo";
		String[] args = {"%TEMP%"};
		WinRsCommandOutput output = runCommand(winRs, command, args, shellId);
		String tempDir = output.getStdout().trim();

		return tempDir + File.separator + ("winrs_" + System.currentTimeMillis());
	}

	private static WinRsCommandOutput runCommand(WinRs winRs, String command, String[] args, String shellId) throws WsmanException {
		String stdout = "";
		String stderr = "";
		long exitCode = 0;
		WinRsCommandState state = WinRsCommandState.Running;
		String commandId = winRs.runCommand(shellId, command, args);
		do {
			WinRsCommandOutput output;
			output = winRs.getCommandOutput(shellId, commandId);
			stdout += output.getStdout();
			stderr += output.getStderr();
			exitCode = output.getExitCode();
			state = output.getState();
		} while (state == WinRsCommandState.Running);
		winRs.cleanupCommand(shellId, commandId);

		return new WinRsCommandOutput(stdout, stderr, exitCode, state);
	}
	
	private static WinRsCommandOutput downloadToTempFile(WinRs winRs, String shellId, String tempFilePath, String filename, String account) throws WsmanException, UnsupportedEncodingException, HinemosUnknown {
		String command = "powershell";
		String[] args = {
				"-inputformat",
				"none",
				"-encodedCommand",
				Base64.encodeBase64String(getDownloadScript(tempFilePath,
						filename, account).getBytes("UTF-16LE")) };
		WinRsCommandOutput output = runCommand(winRs, command, args, shellId);

		return output;
	}
	
	
	private static String getDownloadScript(String tempFilePath, String fileName, String account) {
		String url = HinemosPropertyUtil.getHinemosPropertyStr("infra.transfer.winrm.url", "");
		StringBuffer buffer = new StringBuffer();
		buffer.append(String.format("$temp_file_path = [System.IO.Path]::GetFullPath('%s')\n", tempFilePath));
		buffer.append(String.format("$url = \"%sHinemosWS/InfraEndpoint?wsdl\"\n", url));
		buffer.append("$soap = @'\n");
		buffer.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n");
		buffer.append("    <soap:Body>\n");
		buffer.append("        <downloadTransferFile xmlns=\"http://infra.ws.clustercontrol.com\">\n");
		buffer.append(String.format("            <arg0 xmlns=\"\">%s</arg0>\n", fileName));
		buffer.append("        </downloadTransferFile>\n");
		buffer.append("    </soap:Body>\n");
		buffer.append("</soap:Envelope>\n");
		buffer.append("'@\n");
		buffer.append("$user = \"hinemos\"\n");
		buffer.append("$pass = \"hinemos\"\n");
		buffer.append(String.format("$bytes = [System.Text.Encoding]::ASCII.GetBytes(\"%s\")\n", account));
		buffer.append("$base64 = [System.Convert]::ToBase64String($bytes)\n");
		buffer.append("$headers = @{ Authorization = \"Basic $base64\" }\n");
		buffer.append("$progressPreference = 'silentlyContinue'\n");
		buffer.append("Invoke-WebRequest $url -ContentType \"text/xml\" -Body $soap -Method Post -Headers $headers -OutFile $temp_file_path\n");
		String str = buffer.toString();
		log.debug(str);
		return str;
	}
	
	private static void writeToBinaryFile(String filePath,
			byte[] binaryContent) throws IOException {
		Path path = Paths.get(filePath);
		Files.write(path, binaryContent);
	}
}
