/*
 * This file is part of WebGoat, an Open Web Application Security Project utility. For details, please see http://www.owasp.org/
 *
 * Copyright (c) 2002 - 2019 Bruce Mayhew
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * Getting Source ==============
 *
 * Source for this application is maintained at https://github.com/WebGoat/WebGoat, a repository for free software projects.
 */

package org.owasp.webgoat.webwolf;

import static java.util.Comparator.comparing;
import static org.springframework.http.MediaType.ALL_VALUE;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/** Controller for uploading a file */
@Controller
@Slf4j
public class FileServer {

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Value("${webwolf.fileserver.location}")
  private String fileLocation;

  @Value("${server.address}")
  private String server;

  @Value("${server.servlet.context-path}")
  private String contextPath;

  @Value("${server.port}")
  private int port;

  @RequestMapping(
      path = "/file-server-location",
      consumes = ALL_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  @ResponseBody
  public String getFileLocation() {
    return fileLocation;
  }

  @PostMapping(value = "/fileupload")
  public ModelAndView importFile(
      @RequestParam("file") MultipartFile myFile, Authentication authentication)
      throws IOException {
    String username = authentication.getName();
    var destinationDir = new File(fileLocation, username);
    destinationDir.mkdirs();
    myFile.transferTo(new File(destinationDir, String.valueOf(myFile.getOriginalFilename()).replaceAll("([/\\\\:*?\"<>|])|(^\\s)|([.\\s]$)", "_").replaceAll("\0", "")));
    log.debug("File saved to {}", new File(destinationDir, myFile.getOriginalFilename()));

    return new ModelAndView(
        new RedirectView("files", true),
        new ModelMap().addAttribute("uploadSuccess", "File uploaded successful"));
  }

  @GetMapping(value = "/files")
  public ModelAndView getFiles(
      HttpServletRequest request, Authentication authentication, TimeZone timezone) {
    String username = (null != authentication) ? authentication.getName() : "anonymous";
    File destinationDir = new File(fileLocation, username);

    ModelAndView modelAndView = new ModelAndView();
    modelAndView.setViewName("files");
    File changeIndicatorFile = new File(destinationDir, username + "_changed");
    if (changeIndicatorFile.exists()) {
      modelAndView.addObject("uploadSuccess", request.getParameter("uploadSuccess"));
    }
    changeIndicatorFile.delete();

    record UploadedFile(String name, String size, String link, String creationTime) {}

    var uploadedFiles = new ArrayList<UploadedFile>();
    File[] files = destinationDir.listFiles(File::isFile);
    if (files != null) {
      for (File file : files) {
        String size = FileUtils.byteCountToDisplaySize(file.length());
        String link = String.format("files/%s/%s", username, file.getName());
        uploadedFiles.add(
            new UploadedFile(file.getName(), size, link, getCreationTime(timezone, file)));
      }
    }

    modelAndView.addObject(
        "files",
        uploadedFiles.stream().sorted(comparing(UploadedFile::creationTime).reversed()).toList());
    modelAndView.addObject("webwolf_url", "http://" + server + ":" + port + contextPath);
    return modelAndView;
  }

  private String getCreationTime(TimeZone timezone, File file) {
    try {
      FileTime creationTime = (FileTime) Files.getAttribute(file.toPath(), "creationTime");
      ZonedDateTime zonedDateTime = creationTime.toInstant().atZone(timezone.toZoneId());
      return dateTimeFormatter.format(zonedDateTime);
    } catch (IOException e) {
      return "unknown";
    }
  }
}
