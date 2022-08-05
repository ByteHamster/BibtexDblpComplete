# BibtexDblpComplete

This tool takes a bibtex file and tries to complete each entry using the dblp database.
Uses dblp's "condensed" format, but adds the doi.

Usage:

```
java -jar BibtexDblpComplete.jar paper.bib
```

Example interaction:

```
BibTeXObject 0 of 65
Loading from dblp [condensed]: Hash, Displace, and Compress
Original entry                                               | Suggested change                                 
-----                                                        | -----                                            
@inproceedings{belazzougui2009hash,                          | @inproceedings{belazzougui2009hash,              
    author = {Djamal Belazzougui and Fabiano C. Botelho and… |     author = {Djamal Belazzougui and             
    title = {Hash, Displace, and Compress},                  |         Fabiano C. Botelho and                   
    year = {2009}                                            |         Martin Dietzfelbinger},                  
}                                                            |     title = {Hash, Displace, and Compress},      
                                                             |     booktitle = {{ESA}},                         
                                                             |     series = {Lecture Notes in Computer Science},
                                                             |     volume = {5757},                             
                                                             |     pages = {682--693},                          
                                                             |     publisher = {Springer},                      
                                                             |     year = {2009},                               
                                                             |     doi = {10.1007/978-3-642-04128-0_61}         
                                                             | }                                                

Apply this change? [Ynw]
```
