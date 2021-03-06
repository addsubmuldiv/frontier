load sprintf("%s/colours.plt",tmpldir)
set terminal term 
set output sprintf("%s/%s/tput_vs_fail_prob_stddev%s",outputdir,timestr,termext)

#set xlabel "Failure probability ([0-1])" font ", 20" 
#set ylabel "Throughput (Kb/s)" font ", 20" offset -2
set xlabel "Failure probability ([0-1])" font ", 20" 
set ylabel "Throughput (Kb/s)" font ", 20" offset -2
#set yrange [0:100]

#set logscale x
#set tics font ", 16"

#set border linewidth 1.5
#set style line 1 linewidth 2.5 linecolor rgb CRIMSON 
#set style line 2 linewidth 2.5 linecolor rgb CADMIUMORANGE 
#set style line 3 linewidth 2.5 linecolor rgb COBALT 
#set style line 4 linewidth 2.5 linecolor rgb GREY 
#set style line 5 linewidth 2.5 linecolor rgb "black" 

set style line 1 linewidth 2.5 linecolor rgb "red"
set style line 2 linewidth 2.5 linecolor rgb "blue"
set style line 3 linewidth 2.5 linecolor rgb "green"
set style line 4 linewidth 2.5 linecolor rgb "pink"
set style line 5 linewidth 2.5 linecolor rgb "violet"

#set boxwidth 0.01
#set style fill empty 
#set key spacing 1.75 font ", 16"
#set bmargin 4
#set lmargin 13 

plot sprintf("%s/%s/1k-tput.data",outputdir,timestr) using 1:2 title "k=1" w lines linestyle 1, \
	sprintf("%s/%s/2k-tput.data",outputdir,timestr) using 1:2 title "k=2" w lines linestyle 2, \
	sprintf("%s/%s/3k-tput.data",outputdir,timestr) using 1:2 title "k=3" w lines linestyle 3, \
	sprintf("%s/%s/4k-tput.data",outputdir,timestr) using 1:2 title "k=4" w lines linestyle 4, \
	sprintf("%s/%s/5k-tput.data",outputdir,timestr) using 1:2 title "k=5" w lines linestyle 5, \
	sprintf("%s/%s/1k-tput.data",outputdir,timestr) using 1:2:4 notitle linestyle 1 with yerrorb, \
	sprintf("%s/%s/2k-tput.data",outputdir,timestr) using ($1+0.001):2:4 notitle linestyle 2 with yerrorb, \
	sprintf("%s/%s/3k-tput.data",outputdir,timestr) using ($1+0.0015):2:4 notitle linestyle 3 with yerrorb, \
	sprintf("%s/%s/4k-tput.data",outputdir,timestr) using ($1+0.002):2:4 notitle linestyle 4 with yerrorb, \
	sprintf("%s/%s/5k-tput.data",outputdir,timestr) using ($1+0.0025):2:4 notitle linestyle 5 with yerrorb
