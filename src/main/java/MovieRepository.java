import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MovieRepository {
    private final String URL = "https://www.filmweb.pl";

    public Map<Integer,Movie> getTopList(int moviesCount) throws IOException{
        if (moviesCount > 500 || moviesCount <=0) {
            System.out.println("Invalid input");
            return null;
        } else {
            double maxPage = (double) moviesCount / 25;
            if (moviesCount % maxPage != 0) {
                maxPage++;
            }
            maxPage = (int) maxPage;
            Map<Integer,Movie> listOfMovies = new ConcurrentHashMap<>();
            Elements newUrls = new Elements();
            Elements newRanks = new Elements();
            for (int i=1; i<=maxPage; i++) {
                Connection connectList = Jsoup.connect(URL + "/ajax/ranking/film/" + i);
                Document documentList = connectList.get();
                Elements ranks = documentList.select("span.rankingType__position");
                Elements urls = documentList.select("div:nth-child(3) > div:nth-child(1) > h2:nth-child(1) > a:nth-child(1)");
                newUrls.addAll(urls);
                newRanks.addAll(ranks);
            }
            if (moviesCount % 25 != 0) {
                newUrls = deleteRedundantMovies(newUrls, moviesCount);
            }
            Elements finalNewUrls = newUrls;
            newUrls.parallelStream().forEach((href) -> {
                int rankOfMovie = Integer.parseInt(newRanks.get(finalNewUrls.indexOf(href)).text());
                try {
                    listOfMovies.put(rankOfMovie,getMovieData(href));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            return listOfMovies;
        }
    }

    private Movie getMovieData(Element href) throws IOException {
        Connection connectMovie = Jsoup.connect(URL + href.attr("href"));
        Document documentMovie = connectMovie.get();

//        int rank = Integer.parseInt(documentMovie.select(".worldRanking").text().replaceAll("\\D+",""));
        String title = documentMovie.select(".filmCoverSection__title > span:nth-child(1)").text();
        int year = Integer.parseInt(documentMovie.select(".filmCoverSection__year").text());
        String originalTitle = documentMovie.select(".filmCoverSection__orginalTitle").text();
        double rate = Double.parseDouble(documentMovie.select("span.filmRating__rateValue:nth-child(2)").text().replaceAll(",","."));
        double criticsRate;
        if (documentMovie.select("span.filmRating__rateValue:nth-child(1)").text().contains(",")) {
            criticsRate = Double.parseDouble(documentMovie.select("span.filmRating__rateValue:nth-child(1)").text().replaceAll(",", "."));
        } else {
            criticsRate = -1;
        }
        String length = documentMovie.select(".filmCoverSection__filmTime").text().replaceAll("godz.","h").replaceAll("min.","min");
        String director = documentMovie.select("div.filmInfo__info:nth-child(3)").text().replaceAll("więcej", "");
        String screenwriter;
        String genre;
        String countryOfOrigin;
        if (director.isEmpty()) {
            director = documentMovie.select(".filmPosterSection__info > div:nth-child(2)").text().replaceAll("więcej", "");
            screenwriter = documentMovie.select(".filmPosterSection__info > div:nth-child(4)").text().replaceAll("więcej", "");
            genre = documentMovie.select("div.filmInfo__info:nth-child(6)").text();
            countryOfOrigin = documentMovie.select("div.filmInfo__info:nth-child(8)").text();
        } else {
            screenwriter = documentMovie.select("div.filmInfo__info:nth-child(5)").text().replaceAll("więcej", "");
            genre = documentMovie.select("div.filmInfo__info:nth-child(7)").text();
            countryOfOrigin = documentMovie.select("div.filmInfo__info:nth-child(9)").text();
        }
        return new Movie(title,year,originalTitle,rate,criticsRate,length,director,screenwriter,genre,countryOfOrigin);
    }

    public void exportToExcel(Map<Integer,Movie> map, boolean IsNewExcelFormat) throws IOException{
        Workbook workbook = createWorkbookObject(IsNewExcelFormat);
        Sheet sheet = workbook.createSheet("Toplist");
        setHeader(sheet);

        map.forEach((rank,movie) -> {
            Row row = sheet.createRow(rank);

            for (int i=0; i<=10; i++) {
                Cell cell = row.createCell(i);
                switch (i) {
                    case 0:
                        cell.setCellValue(rank.toString() + ".");
                        break;
                    case 1:
                        cell.setCellValue(movie.getTitle());
                        break;
                    case 2:
                        cell.setCellValue(movie.getYear());
                        break;
                    case 3:
                        cell.setCellValue(movie.getOriginalTitle());
                        break;
                    case 4:
                        cell.setCellValue(movie.getRate());
                        break;
                    case 5:
                        cell.setCellValue(movie.getCriticsRate());
                        break;
                    case 6:
                        cell.setCellValue(movie.getLength());
                        break;
                    case 7:
                        cell.setCellValue(movie.getDirector());
                        break;
                    case 8:
                        cell.setCellValue(movie.getScreenwriter());
                        break;
                    case 9:
                        cell.setCellValue(movie.getGenre());
                        break;
                    case 10:
                        cell.setCellValue(movie.getCountryOfOrigin());
                        break;
                }
            }
        });

        autoSizeColumns(sheet);

        FileOutputStream fileOut = getFileExtension(IsNewExcelFormat);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();
    }

    private Elements deleteRedundantMovies(Elements rawList, int moviesToKeep) {
        Elements readyList = new Elements();
        for (Element url : rawList) {
            if (moviesToKeep == 0) {
                break;
            }
            readyList.add(url);
            moviesToKeep--;
        }
        return readyList;
    }

    private void autoSizeColumns(Sheet sheet) {
        for(int i = 0; i < getHeaders().length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void setHeader(Sheet sheet) {
        Row rowHeader = sheet.createRow(0);
        rowHeader.setHeightInPoints(30);
        String[] headers = getHeaders();
        for (int i=0; i<11; i++) {
            rowHeader.createCell(i).setCellValue(headers[i]);
        }
    }

    private Workbook createWorkbookObject(boolean IsNewExcelFormat) {
            if (IsNewExcelFormat) {
                return new XSSFWorkbook();
            } else {
                return new HSSFWorkbook();
            }
    }

    private FileOutputStream getFileExtension(boolean IsNewExcelFormat) throws IOException{
        if (IsNewExcelFormat) {
            return new FileOutputStream("toplist.xlsx");
        } else {
            return new FileOutputStream("toplist.xls");
        }
    }

    private String[] getHeaders(){
        String[] array;
        array = new String [11];
        array[0] = "Rank";
        array[1] = "Title";
        array[2] = "Year";
        array[3] = "Original title";
        array[4] = "Rate";
        array[5] = "Critics' rate";
        array[6] = "Length";
        array[7] = "Director";
        array[8] = "Screenwriter";
        array[9] = "Genre";
        array[10] = "Country of origin";
        return array;
    }
}
