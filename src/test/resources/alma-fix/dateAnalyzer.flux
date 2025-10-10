"src/test/resources/alma-fix"
| read-dir(filenamepattern=".*.xml")
| open-file
| decode-xml
| handle-marcxml(ignorenamespace="true")
| fix(FLUX_DIR + "date.fix")
| flatten
| stream-tee
| {encode-csv
    | write(FLUX_DIR + "date-analyze.csv")
    }
    {fix("vacuum()")
    |list-fix-paths
    |print(header="Count all\n")
    }
    {fix("
        vacuum()
        unless any_match('008-date','.\\\\d{4}.*')
            reject()
        end
        if exists('264-date')
            nothing()
        elsif exists('260-date')
            nothing()
        else
            reject()
        end
    ")
    | list-fix-paths
    |print(header="Only correct 008 dates\n")
    }
    {fix("
        vacuum()
        if any_contain('264-date','[')
            nothing()
        elsif any_contain('260-date','[')
            nothing()
        else
            reject()
        end
    ")
    | list-fix-paths
    |print(header="Dates with brakets\n")
    }

;
