
package dcsNetOp.csv2xml;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import static java.nio.file.FileVisitResult.*;
import static java.nio.file.FileVisitOption.*;
import java.util.*;


public class Finder extends SimpleFileVisitor<Path>
{


        private final PathMatcher matcher;
        private ArrayList<Path> matched;

        //syntax should be either "glob:" or "regex:"
        Finder(String syntax, String pattern) {

            if ((syntax == null) || (syntax.isEmpty()))
              syntax = "glob:";
            matcher = FileSystems.getDefault()
                    .getPathMatcher(syntax + pattern);
            this.matched  = new ArrayList<Path>();
        }

        Finder(String syntaxPattern) {

            matcher = FileSystems.getDefault()
                    .getPathMatcher(syntaxPattern);
            this.matched  = new ArrayList<Path>();
        }

        // Compares the glob pattern against
        // the file or directory name.
        void find(Path file) {
            Path name = file.getFileName();
            if (name != null && matcher.matches(name)) {
                matched.add(file);
            }
        }

        // get matched list
        ArrayList<Path> getMatched() 
        {
          return matched;
        }

       int getNumOfMatched()
       {
         return matched.size();
       }

        // Invoke the pattern matching
        // method on each file.
        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attrs) {
            find(file);
            return CONTINUE;
        }

        // Invoke the pattern matching
        // method on each directory.
        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                BasicFileAttributes attrs) {
            find(dir);
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file,
                IOException exc) {
            System.err.println(exc);
            return CONTINUE;
        }

}

