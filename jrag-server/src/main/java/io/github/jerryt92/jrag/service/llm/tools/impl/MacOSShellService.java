//package io.github.jerryt92.jrag.service.llm.tools.impl;
//
//import io.github.jerryt92.jrag.model.FunctionCallingModel;
//import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//
///**
// * @author jerryt92
// */
//@Component
//public class MacOSShellService extends ToolInterface {
//    private static final Logger logger = LoggerFactory.getLogger(MacOSShellService.class);
//
//    private static final String SUDO_PASSWORD = "569302";
//
//    public MacOSShellService() {
//        toolInfo.setName("execute_mac_os_shell")
//                .setDescription("Executes any macOS shell command and returns the output.")
//                .setParameters(
//                        Collections.singletonList(
//                                new FunctionCallingModel.Tool.Parameter()
//                                        .setName("command")
//                                        .setType("string")
//                                        .setDescription("The macOS zsh command to execute.")
//                        )
//                );
//    }
//
//    @Override
//    public List<String> apply(List<Map<String, Object>> requests) {
//        List<String> results = new ArrayList<>();
//        for (Map<String, Object> request : requests) {
//            String command = (String) request.get("command");
//            boolean isSudo = command.startsWith("sudo ");
//            try {
//                logger.info("Executing command: " + command);
//                // 使用 sudo -S 来确保从标准输入读取密码
//                if (isSudo) {
//                    command = command.replace("sudo ", "sudo -S ");
//                }
//                Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
//                if (isSudo) {
//                    OutputStream outputStream = process.getOutputStream();
//                    outputStream.write((SUDO_PASSWORD + "\n").getBytes());
//                    outputStream.flush();
//                    outputStream.close();
//                }
//                // 启动线程读取标准输出和错误输出
//                StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
//                StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
//                outputGobbler.start();
//                errorGobbler.start();
//                int exitCode = process.waitFor();
//                outputGobbler.join();
//                errorGobbler.join();
//                if (exitCode == 0) {
//                    String result = outputGobbler.getContent();
//                    result += "\nExecution completed successfully.";
//                    logger.info("Command result: " + result);
//                    results.add(result);
//                } else {
//                    String error = errorGobbler.getContent();
//                    logger.error("Command failed with error: " + error);
//                    results.add("Command failed: " + error);
//                }
//            } catch (Exception e) {
//                logger.error("Failed to execute command: " + request, e);
//                results.add(e.getMessage());
//            }
//        }
//        return results;
//    }
//
//    // 辅助类用于读取流内容
//    class StreamGobbler extends Thread {
//        private InputStream inputStream;
//        private StringBuilder content = new StringBuilder();
//        private String type;
//
//        public StreamGobbler(InputStream inputStream, String type) {
//            this.inputStream = inputStream;
//            this.type = type;
//        }
//
//        @Override
//        public void run() {
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    content.append(line).append("\n");
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        public String getContent() {
//            return content.toString();
//        }
//    }
//}
//
